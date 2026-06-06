package com.vulkantechtt.konvo.scheduling;

import com.vulkantechtt.konvo.audit.AuditAction;
import com.vulkantechtt.konvo.audit.AuditService;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.conversations.Conversation;
import com.vulkantechtt.konvo.conversations.ConversationRepository;
import com.vulkantechtt.konvo.conversations.OutboundMessageCommand;
import com.vulkantechtt.konvo.conversations.OutboundMessageDispatcher;
import com.vulkantechtt.konvo.customers.Customer;
import com.vulkantechtt.konvo.customers.CustomerRepository;
import com.vulkantechtt.konvo.notifications.NotificationService;
import com.vulkantechtt.konvo.notifications.NotificationType;
import com.vulkantechtt.konvo.scheduling.dto.AppointmentResponse;
import com.vulkantechtt.konvo.scheduling.dto.AvailabilityResponse;
import com.vulkantechtt.konvo.scheduling.dto.SchedulingSettingsResponse;
import com.vulkantechtt.konvo.scheduling.dto.UpdateSchedulingSettingsRequest;
import com.vulkantechtt.konvo.scheduling.google.GoogleCalendarClient;
import com.vulkantechtt.konvo.scheduling.google.GoogleTokenService;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-scoped scheduling configuration: the single shared Google Calendar
 * connection (managed by the OAuth endpoints) plus the Calendly fallback link
 * and booking knobs. Booking mode is derived as the GOOGLE → LINK → DISABLED
 * fallback chain ({@link #bookingMode}).
 *
 * <p>Availability computation and the actual book/cancel flow are layered on in
 * the later phases; this class owns the settings row lifecycle.
 */
@Service
public class SchedulingService {

    private static final Logger log = LoggerFactory.getLogger(SchedulingService.class);
    private static final int MAX_SLOTS = 12;

    private final SchedulingSettingsRepository settings;
    private final AppointmentRepository appointments;
    private final CustomerRepository customers;
    private final ConversationRepository conversations;
    private final GoogleCalendarClient googleClient;
    private final GoogleTokenService tokens;
    private final OutboundMessageDispatcher dispatcher;
    private final NotificationService notifications;
    private final AuditService audit;

    public SchedulingService(SchedulingSettingsRepository settings,
                             AppointmentRepository appointments,
                             CustomerRepository customers,
                             ConversationRepository conversations,
                             GoogleCalendarClient googleClient,
                             GoogleTokenService tokens,
                             OutboundMessageDispatcher dispatcher,
                             NotificationService notifications,
                             AuditService audit) {
        this.settings = settings;
        this.appointments = appointments;
        this.customers = customers;
        this.conversations = conversations;
        this.googleClient = googleClient;
        this.tokens = tokens;
        this.dispatcher = dispatcher;
        this.notifications = notifications;
        this.audit = audit;
    }

    /** Loads the tenant row, creating a defaults row on first access. */
    @Transactional
    public SchedulingSettings getOrCreate(UUID tenantId) {
        return settings.findByTenantId(tenantId).orElseGet(() -> {
            SchedulingSettings s = new SchedulingSettings();
            s.setTenantId(tenantId);
            return settings.save(s);
        });
    }

    @Transactional
    public SchedulingSettingsResponse get(UUID tenantId) {
        return toResponse(getOrCreate(tenantId));
    }

    @Transactional
    public SchedulingSettingsResponse update(KonvoPrincipal actor, UpdateSchedulingSettingsRequest req) {
        SchedulingSettings s = getOrCreate(actor.tenantId());
        if (req.workDayEndHour() <= req.workDayStartHour()) {
            throw com.vulkantechtt.konvo.common.KonvoException.badRequest(
                    "Work day end hour must be after the start hour");
        }
        s.setCalendlyUrl(blankToNull(req.calendlyUrl()));
        s.setMeetingDurationMinutes(req.meetingDurationMinutes());
        s.setTimezone(blankToDefault(req.timezone(), "UTC"));
        s.setBookingWindowDays(req.bookingWindowDays());
        s.setWorkDayStartHour(req.workDayStartHour());
        s.setWorkDayEndHour(req.workDayEndHour());
        s.setWorkDays(blankToDefault(req.workDays(), "MON,TUE,WED,THU,FRI"));
        SchedulingSettings saved = settings.save(s);
        audit.record(actor, AuditAction.SCHEDULING_SETTINGS_UPDATED, saved.getId(),
                "Updated scheduling settings",
                Map.of("bookingMode", bookingMode(saved).name(),
                        "calendlyLinkSet", saved.getCalendlyUrl() != null,
                        "meetingDurationMinutes", saved.getMeetingDurationMinutes()));
        return toResponse(saved);
    }

    /**
     * Persists a completed Google connection from the OAuth callback. There is
     * no {@link KonvoPrincipal} here (the redirect carries no JWT), so the audit
     * entry is recorded as a system action scoped to the tenant.
     */
    @Transactional
    public void connectGoogle(UUID tenantId, String accountEmail, GoogleCalendarClient.TokenResult tokens) {
        SchedulingSettings s = getOrCreate(tenantId);
        String refresh = tokens.refreshToken();
        if ((refresh == null || refresh.isBlank()) && s.getGoogleRefreshToken() == null) {
            // Google only returns a refresh token on first consent; we force
            // prompt=consent so this should not happen — if it does, the user
            // must retry rather than us storing a non-refreshable connection.
            throw new KonvoException(HttpStatus.BAD_GATEWAY, "google_no_refresh_token",
                    "Google did not return a refresh token. Please try connecting again.");
        }
        if (refresh != null && !refresh.isBlank()) {
            s.setGoogleRefreshToken(refresh);
        }
        s.setGoogleAccessToken(tokens.accessToken());
        s.setGoogleTokenExpiresAt(tokens.expiresAt());
        s.setGoogleAccountEmail(accountEmail);
        s.setGoogleConnected(true);
        SchedulingSettings saved = settings.save(s);
        audit.recordSystem(tenantId, AuditAction.SCHEDULING_GOOGLE_CONNECTED, saved.getId(),
                "Connected Google Calendar" + (accountEmail != null ? " (" + accountEmail + ")" : ""),
                Map.of("accountEmail", accountEmail == null ? "" : accountEmail));
    }

    @Transactional
    public SchedulingSettingsResponse disconnectGoogle(KonvoPrincipal actor) {
        SchedulingSettings s = getOrCreate(actor.tenantId());
        String previousEmail = s.getGoogleAccountEmail();
        s.setGoogleConnected(false);
        s.setGoogleRefreshToken(null);
        s.setGoogleAccessToken(null);
        s.setGoogleTokenExpiresAt(null);
        s.setGoogleAccountEmail(null);
        SchedulingSettings saved = settings.save(s);
        Map<String, Object> diff = new HashMap<>();
        diff.put("accountEmail", previousEmail == null ? "" : previousEmail);
        audit.record(actor, AuditAction.SCHEDULING_GOOGLE_DISCONNECTED, saved.getId(),
                "Disconnected Google Calendar", diff);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public BookingMode bookingMode(UUID tenantId) {
        return settings.findByTenantId(tenantId).map(SchedulingService::bookingMode).orElse(BookingMode.DISABLED);
    }

    // --- availability ------------------------------------------------------

    /**
     * Open slots for the workspace's connected calendar. Returns an empty slot
     * list (with the booking mode) when Google isn't the active mode.
     */
    @Transactional
    public AvailabilityResponse availability(UUID tenantId) {
        SchedulingSettings s = getOrCreate(tenantId);
        BookingMode mode = bookingMode(s);
        if (mode != BookingMode.GOOGLE) {
            return new AvailabilityResponse(mode, s.getMeetingDurationMinutes(), s.getTimezone(),
                    s.getCalendlyUrl(), List.of());
        }
        List<Instant> slots = computeSlots(s);
        return new AvailabilityResponse(mode, s.getMeetingDurationMinutes(), s.getTimezone(),
                s.getCalendlyUrl(), slots);
    }

    /** Shared slot computation used by the availability API and Vee. */
    @Transactional
    public List<Instant> computeSlots(SchedulingSettings s) {
        ZoneId zone = safeZone(s.getTimezone());
        Instant now = Instant.now();
        Instant windowEnd = now.plus(java.time.Duration.ofDays(s.getBookingWindowDays() + 1L));
        String accessToken = tokens.validAccessToken(s);
        List<GoogleCalendarClient.BusyInterval> busy =
                googleClient.freeBusy(accessToken, s.getGoogleCalendarId(), now, windowEnd);
        return AvailabilityPlanner.slots(now, zone,
                AvailabilityPlanner.parseWorkDays(s.getWorkDays()),
                s.getWorkDayStartHour(), s.getWorkDayEndHour(),
                s.getMeetingDurationMinutes(), s.getBookingWindowDays(),
                busy, MAX_SLOTS);
    }

    // --- booking -----------------------------------------------------------

    /**
     * Books a meeting: creates the Google event, persists the appointment,
     * audits, notifies owners/admins, and (when a conversation is linked) sends
     * the customer a WhatsApp confirmation. Requires GOOGLE booking mode.
     *
     * @param actor the booking agent, or {@code null} for a Vee-driven booking.
     */
    @Transactional
    public AppointmentResponse book(UUID tenantId, UUID customerId, UUID conversationId,
                                    Instant startsAt, Integer durationMinutes,
                                    String title, String notes,
                                    AppointmentSource source, KonvoPrincipal actor) {
        SchedulingSettings s = getOrCreate(tenantId);
        if (bookingMode(s) != BookingMode.GOOGLE) {
            throw new KonvoException(HttpStatus.CONFLICT, "calendar_not_connected",
                    "Connect Google Calendar to book meetings");
        }
        if (startsAt == null || !startsAt.isAfter(Instant.now())) {
            throw KonvoException.badRequest("Choose a time in the future");
        }
        Customer customer = customers.findById(customerId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> KonvoException.notFound("Customer", customerId));

        int duration = durationMinutes != null && durationMinutes > 0
                ? durationMinutes : s.getMeetingDurationMinutes();
        Instant endsAt = startsAt.plus(java.time.Duration.ofMinutes(duration));
        String customerName = customerName(customer);
        String resolvedTitle = title != null && !title.isBlank()
                ? title.trim() : "Meeting with " + customerName;

        String accessToken = tokens.validAccessToken(s);
        String eventId = googleClient.insertEvent(accessToken, s.getGoogleCalendarId(),
                new GoogleCalendarClient.EventRequest(
                        resolvedTitle, notes, startsAt, endsAt, s.getTimezone(), null));

        Appointment appt = new Appointment();
        appt.setTenantId(tenantId);
        appt.setCustomerId(customerId);
        appt.setConversationId(conversationId);
        appt.setGoogleEventId(eventId);
        appt.setTitle(resolvedTitle);
        appt.setNotes(notes);
        appt.setStartsAt(startsAt);
        appt.setEndsAt(endsAt);
        appt.setStatus(AppointmentStatus.booked);
        appt.setSource(source);
        appt.setCreatedByUserId(actor != null ? actor.userId() : null);
        Appointment saved = appointments.save(appt);

        String whenText = formatSlot(startsAt, safeZone(s.getTimezone()));
        String link = conversationId != null
                ? "/app/inbox?c=" + conversationId : "/app/customers";
        notifications.broadcastToOwnersAndAdmins(tenantId, NotificationType.APPOINTMENT_BOOKED,
                "Meeting booked with " + customerName,
                resolvedTitle + " · " + whenText, link);

        Map<String, Object> diff = new HashMap<>();
        diff.put("customer", customerName);
        diff.put("startsAt", startsAt.toString());
        diff.put("source", source.name());
        if (actor != null) {
            audit.record(actor, AuditAction.APPOINTMENT_BOOKED, saved.getId(),
                    "Booked " + resolvedTitle + " (" + whenText + ")", diff);
        } else {
            audit.recordSystem(tenantId, AuditAction.APPOINTMENT_BOOKED, saved.getId(),
                    "Vee booked " + resolvedTitle + " (" + whenText + ")", diff);
        }

        sendCustomerConfirmation(tenantId, conversationId, customer,
                "✅ You're booked for " + whenText + ". See you then!");

        return toResponse(saved, customerName);
    }

    /** Cancels an appointment: removes the Google event (best effort) and marks it cancelled. */
    @Transactional
    public AppointmentResponse cancel(KonvoPrincipal actor, UUID appointmentId) {
        UUID tenantId = actor.tenantId();
        Appointment appt = appointments.findById(appointmentId)
                .filter(a -> a.getTenantId().equals(tenantId))
                .orElseThrow(() -> KonvoException.notFound("Appointment", appointmentId));
        if (appt.getStatus() == AppointmentStatus.cancelled) {
            return toResponse(appt, customerNameById(appt.getCustomerId()));
        }
        if (appt.getGoogleEventId() != null) {
            SchedulingSettings s = getOrCreate(tenantId);
            if (s.isGoogleConnected()) {
                try {
                    googleClient.deleteEvent(tokens.validAccessToken(s),
                            s.getGoogleCalendarId(), appt.getGoogleEventId());
                } catch (RuntimeException e) {
                    log.warn("Could not delete Google event {} for appointment {}: {}",
                            appt.getGoogleEventId(), appointmentId, e.toString());
                }
            }
        }
        appt.setStatus(AppointmentStatus.cancelled);
        Appointment saved = appointments.save(appt);
        audit.record(actor, AuditAction.APPOINTMENT_CANCELLED, saved.getId(),
                "Cancelled " + saved.getTitle(),
                Map.of("startsAt", saved.getStartsAt().toString()));
        return toResponse(saved, customerNameById(saved.getCustomerId()));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> list(UUID tenantId, UUID customerId, AppointmentStatus status) {
        List<Appointment> rows;
        if (customerId != null) {
            rows = appointments.findByTenantIdAndCustomerIdOrderByStartsAtDesc(tenantId, customerId);
        } else if (status != null) {
            rows = appointments.findByTenantIdAndStatusOrderByStartsAtDesc(tenantId, status);
        } else {
            rows = appointments.findByTenantIdOrderByStartsAtDesc(tenantId);
        }
        return rows.stream()
                .map(a -> toResponse(a, customerNameById(a.getCustomerId())))
                .toList();
    }

    /** Sends a customer-facing WhatsApp message via the linked conversation, if any. */
    private void sendCustomerConfirmation(UUID tenantId, UUID conversationId, Customer customer, String body) {
        if (conversationId == null) {
            return;
        }
        Conversation conv = conversations.findById(conversationId).orElse(null);
        if (conv == null || !conv.getTenantId().equals(tenantId)) {
            return;
        }
        dispatcher.enqueue(new OutboundMessageCommand(
                null, tenantId, conversationId, conv.getChannelId(),
                customer.getId(), customer.getPhone(), body));
    }

    private String customerName(Customer c) {
        if (c.getDisplayName() != null && !c.getDisplayName().isBlank()) {
            return c.getDisplayName();
        }
        if (c.getProfileName() != null && !c.getProfileName().isBlank()) {
            return c.getProfileName();
        }
        return c.getPhone();
    }

    private String customerNameById(UUID customerId) {
        return customers.findById(customerId).map(this::customerName).orElse("Customer");
    }

    private AppointmentResponse toResponse(Appointment a, String customerName) {
        return new AppointmentResponse(
                a.getId(), a.getCustomerId(), customerName, a.getConversationId(),
                a.getTitle(), a.getNotes(), a.getStartsAt(), a.getEndsAt(),
                a.getStatus(), a.getSource(), a.getGoogleEventId() != null, a.getCreatedAt());
    }

    static ZoneId safeZone(String tz) {
        try {
            return ZoneId.of(tz);
        } catch (RuntimeException e) {
            return ZoneId.of("UTC");
        }
    }

    /** Human slot label, e.g. "Tue 10 Jun, 2:00 PM". */
    static String formatSlot(Instant when, ZoneId zone) {
        return DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH)
                .format(when.atZone(zone));
    }

    static BookingMode bookingMode(SchedulingSettings s) {
        if (s.isGoogleConnected()) {
            return BookingMode.GOOGLE;
        }
        if (s.getCalendlyUrl() != null && !s.getCalendlyUrl().isBlank()) {
            return BookingMode.LINK;
        }
        return BookingMode.DISABLED;
    }

    static SchedulingSettingsResponse toResponse(SchedulingSettings s) {
        return new SchedulingSettingsResponse(
                bookingMode(s),
                s.isGoogleConnected(),
                s.getGoogleAccountEmail(),
                s.getGoogleCalendarId(),
                s.getCalendlyUrl(),
                s.getMeetingDurationMinutes(),
                s.getTimezone(),
                s.getBookingWindowDays(),
                s.getWorkDayStartHour(),
                s.getWorkDayEndHour(),
                s.getWorkDays());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String blankToDefault(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s.trim();
    }
}
