package com.vulkantechtt.konvo.scheduling;

import com.vulkantechtt.konvo.conversations.Conversation;
import com.vulkantechtt.konvo.conversations.ConversationRepository;
import com.vulkantechtt.konvo.conversations.OutboundMessageCommand;
import com.vulkantechtt.konvo.conversations.OutboundMessageDispatcher;
import com.vulkantechtt.konvo.scheduling.dto.AvailabilityResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Vee's autonomous booking flow. Hooked into the AI reply pipeline before the
 * normal knowledge-base reply: when scheduling is enabled and the customer shows
 * booking intent, Vee either shares the Calendly link (LINK mode) or offers real
 * Google slots and books the one the customer picks (GOOGLE mode). A small state
 * machine is persisted on {@code conversations.pending_booking} so the multi-turn
 * exchange survives across inbound messages.
 *
 * {@link #handle} returns {@code true} when it produced a reply (the caller
 * should then skip the normal AI reply), {@code false} to fall through.
 */
@Service
public class VeeBookingService {

    private static final Logger log = LoggerFactory.getLogger(VeeBookingService.class);
    private static final int MAX_OFFER = 4;
    private static final Pattern FIRST_NUMBER = Pattern.compile("\\b(\\d{1,2})\\b");
    private static final List<String> INTENT_KEYWORDS = List.of(
            "book", "appointment", "schedule", "scheduling", "meeting", "demo",
            "consultation", "reschedule");

    private final SchedulingService scheduling;
    private final ConversationRepository conversations;
    private final OutboundMessageDispatcher dispatcher;
    private final ObjectMapper json;

    public VeeBookingService(SchedulingService scheduling,
                             ConversationRepository conversations,
                             OutboundMessageDispatcher dispatcher,
                             ObjectMapper json) {
        this.scheduling = scheduling;
        this.conversations = conversations;
        this.dispatcher = dispatcher;
        this.json = json;
    }

    public boolean handle(Conversation conv, String customerPhone, String inboundBody) {
        if (inboundBody == null || inboundBody.isBlank()) {
            return false;
        }
        AvailabilityResponse avail;
        try {
            avail = scheduling.availability(conv.getTenantId());
        } catch (RuntimeException e) {
            // Availability lookup failed (e.g. Google token issue) — don't break the
            // normal reply; just skip the booking flow this turn.
            log.warn("Vee booking: availability lookup failed for tenant {}: {}",
                    conv.getTenantId(), e.toString());
            return false;
        }
        if (avail.bookingMode() == BookingMode.DISABLED) {
            return false;
        }
        ZoneId zone = SchedulingService.safeZone(avail.timezone());

        PendingState pending = readPending(conv.getPendingBooking());
        if (pending != null && !pending.offered().isEmpty()) {
            return continueFlow(conv, customerPhone, inboundBody, pending, zone);
        }

        if (!hasBookingIntent(inboundBody)) {
            return false;
        }
        return startFlow(conv, customerPhone, avail, zone);
    }

    // --- offering ----------------------------------------------------------

    private boolean startFlow(Conversation conv, String customerPhone,
                              AvailabilityResponse avail, ZoneId zone) {
        if (avail.bookingMode() == BookingMode.LINK) {
            send(conv, customerPhone, "Happy to help you book a time! You can grab a slot here: "
                    + avail.calendlyUrl());
            return true;
        }
        // GOOGLE
        List<String> offered = avail.slots().stream().limit(MAX_OFFER).map(Instant::toString).toList();
        if (offered.isEmpty()) {
            send(conv, customerPhone, "I don't have any open times right now, but I'll get the team "
                    + "to follow up and schedule with you.");
            return true;
        }
        savePending(conv, new PendingState(offered, false));
        send(conv, customerPhone, "I'd be happy to set up a meeting. " + offerText(offered, zone));
        return true;
    }

    private boolean continueFlow(Conversation conv, String customerPhone, String inboundBody,
                                 PendingState pending, ZoneId zone) {
        Integer choice = parseChoice(inboundBody, pending.offered().size());
        if (choice != null) {
            Instant slot = Instant.parse(pending.offered().get(choice - 1));
            try {
                // source=vee, no actor; book() sends the WhatsApp confirmation itself.
                scheduling.book(conv.getTenantId(), conv.getCustomerId(), conv.getId(),
                        slot, null, null, null, AppointmentSource.vee, null);
                clearPending(conv);
            } catch (RuntimeException e) {
                log.warn("Vee booking: book failed for conversation {}: {}", conv.getId(), e.toString());
                clearPending(conv);
                send(conv, customerPhone, "Sorry, that time just became unavailable. "
                        + "Let me know if you'd like to pick another.");
            }
            return true;
        }
        // Not a recognisable choice — re-offer once, then hand back to the normal flow.
        if (!pending.reoffered()) {
            savePending(conv, new PendingState(pending.offered(), true));
            send(conv, customerPhone, "Sorry, I didn't catch that. " + offerText(pending.offered(), zone));
            return true;
        }
        clearPending(conv);
        return false;
    }

    private String offerText(List<String> offered, ZoneId zone) {
        StringBuilder sb = new StringBuilder("Here are some times — reply with the number that works:");
        for (int i = 0; i < offered.size(); i++) {
            sb.append("\n").append(i + 1).append(") ")
                    .append(SchedulingService.formatSlot(Instant.parse(offered.get(i)), zone));
        }
        return sb.toString();
    }

    // --- parsing -----------------------------------------------------------

    static boolean hasBookingIntent(String body) {
        String lower = body.toLowerCase();
        return INTENT_KEYWORDS.stream().anyMatch(lower::contains);
    }

    /** Pulls a 1-based slot choice from the message, or null if none in range. */
    static Integer parseChoice(String body, int size) {
        Matcher m = FIRST_NUMBER.matcher(body);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n >= 1 && n <= size) {
                return n;
            }
        }
        return null;
    }

    // --- state persistence -------------------------------------------------

    private PendingState readPending(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            PendingState s = json.readValue(raw, PendingState.class);
            return s != null && s.offered() != null ? s : null;
        } catch (RuntimeException e) {
            log.warn("Vee booking: could not parse pending_booking, ignoring: {}", e.toString());
            return null;
        }
    }

    private void savePending(Conversation conv, PendingState state) {
        try {
            conv.setPendingBooking(json.writeValueAsString(state));
        } catch (RuntimeException e) {
            log.warn("Vee booking: could not serialise pending_booking: {}", e.toString());
            conv.setPendingBooking(null);
        }
        conversations.save(conv);
    }

    private void clearPending(Conversation conv) {
        conv.setPendingBooking(null);
        conversations.save(conv);
    }

    private void send(Conversation conv, String customerPhone, String body) {
        dispatcher.enqueue(new OutboundMessageCommand(
                null, conv.getTenantId(), conv.getId(), conv.getChannelId(),
                conv.getCustomerId(), customerPhone, body));
    }

    /** Persisted booking state: the offered slot ISO instants and whether we've re-offered once. */
    public record PendingState(List<String> offered, boolean reoffered) {
        public PendingState {
            offered = offered == null ? new ArrayList<>() : offered;
        }
    }
}
