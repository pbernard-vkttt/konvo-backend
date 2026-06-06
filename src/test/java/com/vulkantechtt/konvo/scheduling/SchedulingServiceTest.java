package com.vulkantechtt.konvo.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.vulkantechtt.konvo.scheduling.google.GoogleCalendarClient;
import com.vulkantechtt.konvo.scheduling.google.GoogleTokenService;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.users.Role;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchedulingServiceTest {

    @Mock SchedulingSettingsRepository settings;
    @Mock AppointmentRepository appointments;
    @Mock CustomerRepository customers;
    @Mock ConversationRepository conversations;
    @Mock GoogleCalendarClient googleClient;
    @Mock GoogleTokenService tokens;
    @Mock OutboundMessageDispatcher dispatcher;
    @Mock NotificationService notifications;
    @Mock AuditService audit;

    private SchedulingService service;

    @BeforeEach
    void setUp() {
        service = new SchedulingService(settings, appointments, customers, conversations,
                googleClient, tokens, dispatcher, notifications, audit);
    }

    private static KonvoPrincipal principal(UUID tenantId) {
        return new KonvoPrincipal(UUID.randomUUID(), "agent@x.tt", "Agent", tenantId, Role.AGENT);
    }

    private static SchedulingSettings connectedSettings(UUID tenantId) {
        SchedulingSettings s = new SchedulingSettings();
        s.setTenantId(tenantId);
        s.setGoogleConnected(true);
        s.setGoogleRefreshToken("refresh");
        s.setGoogleCalendarId("primary");
        s.setTimezone("UTC");
        s.setMeetingDurationMinutes(30);
        return s;
    }

    @Test
    void bookCreatesEventPersistsNotifiesAndConfirms() {
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        Instant startsAt = Instant.now().plusSeconds(86_400);

        when(settings.findByTenantId(tenantId)).thenReturn(Optional.of(connectedSettings(tenantId)));

        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setTenantId(tenantId);
        customer.setPhone("+18681234567");
        customer.setDisplayName("Jane Doe");
        when(customers.findById(customerId)).thenReturn(Optional.of(customer));

        Conversation conv = new Conversation();
        conv.setId(conversationId);
        conv.setTenantId(tenantId);
        conv.setChannelId(channelId);
        conv.setCustomerId(customerId);
        when(conversations.findById(conversationId)).thenReturn(Optional.of(conv));

        when(tokens.validAccessToken(any())).thenReturn("access-token");
        when(googleClient.insertEvent(eq("access-token"), eq("primary"), any())).thenReturn("evt-123");
        when(appointments.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        AppointmentResponse resp = service.book(tenantId, customerId, conversationId,
                startsAt, null, null, "notes", AppointmentSource.agent, principal(tenantId));

        assertThat(resp.onGoogleCalendar()).isTrue();
        assertThat(resp.status()).isEqualTo(AppointmentStatus.booked);
        assertThat(resp.source()).isEqualTo(AppointmentSource.agent);
        assertThat(resp.title()).isEqualTo("Meeting with Jane Doe");

        verify(googleClient).insertEvent(eq("access-token"), eq("primary"), any());
        verify(notifications).broadcastToOwnersAndAdmins(eq(tenantId),
                eq(NotificationType.APPOINTMENT_BOOKED), any(), any(), any());
        verify(audit).record(any(), eq(AuditAction.APPOINTMENT_BOOKED), any(), any(), any());
        verify(dispatcher).enqueue(any(OutboundMessageCommand.class));
    }

    @Test
    void bookRejectedWhenGoogleNotConnected() {
        UUID tenantId = UUID.randomUUID();
        SchedulingSettings s = new SchedulingSettings();
        s.setTenantId(tenantId);
        s.setGoogleConnected(false); // no calendar, no calendly => DISABLED
        when(settings.findByTenantId(tenantId)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.book(tenantId, UUID.randomUUID(), null,
                Instant.now().plusSeconds(3600), null, null, null,
                AppointmentSource.agent, principal(tenantId)))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("Connect Google Calendar");

        verify(googleClient, never()).insertEvent(any(), any(), any());
        verify(appointments, never()).save(any());
    }

    @Test
    void bookRejectsPastTimes() {
        UUID tenantId = UUID.randomUUID();
        when(settings.findByTenantId(tenantId)).thenReturn(Optional.of(connectedSettings(tenantId)));

        assertThatThrownBy(() -> service.book(tenantId, UUID.randomUUID(), null,
                Instant.now().minusSeconds(3600), null, null, null,
                AppointmentSource.agent, principal(tenantId)))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("future");

        verify(appointments, never()).save(any());
    }

    @Test
    void bookingModeFallsBackToLinkThenDisabled() {
        UUID tenantId = UUID.randomUUID();

        SchedulingSettings link = new SchedulingSettings();
        link.setTenantId(tenantId);
        link.setCalendlyUrl("https://calendly.com/acme/intro");
        when(settings.findByTenantId(tenantId)).thenReturn(Optional.of(link));
        assertThat(service.bookingMode(tenantId)).isEqualTo(BookingMode.LINK);

        SchedulingSettings none = new SchedulingSettings();
        none.setTenantId(tenantId);
        when(settings.findByTenantId(tenantId)).thenReturn(Optional.of(none));
        assertThat(service.bookingMode(tenantId)).isEqualTo(BookingMode.DISABLED);
    }
}
