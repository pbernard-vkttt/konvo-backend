package com.vulkantechtt.konvo.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.conversations.Conversation;
import com.vulkantechtt.konvo.conversations.ConversationRepository;
import com.vulkantechtt.konvo.conversations.OutboundMessageCommand;
import com.vulkantechtt.konvo.conversations.OutboundMessageDispatcher;
import com.vulkantechtt.konvo.scheduling.dto.AvailabilityResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class VeeBookingServiceTest {

    @Mock SchedulingService scheduling;
    @Mock ConversationRepository conversations;
    @Mock OutboundMessageDispatcher dispatcher;

    private final ObjectMapper json = new ObjectMapper();
    private VeeBookingService svc;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID channelId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        svc = new VeeBookingService(scheduling, conversations, dispatcher, json);
    }

    private Conversation conversation() {
        Conversation c = new Conversation();
        c.setId(conversationId);
        c.setTenantId(tenantId);
        c.setChannelId(channelId);
        c.setCustomerId(customerId);
        return c;
    }

    private static AvailabilityResponse google(List<Instant> slots) {
        return new AvailabilityResponse(BookingMode.GOOGLE, 30, "UTC", null, slots);
    }

    @Test
    void intentDetectionAndChoiceParsing() {
        assertThat(VeeBookingService.hasBookingIntent("Can I book a demo?")).isTrue();
        assertThat(VeeBookingService.hasBookingIntent("I'd like to schedule a meeting")).isTrue();
        assertThat(VeeBookingService.hasBookingIntent("hello there, thanks")).isFalse();

        assertThat(VeeBookingService.parseChoice("number 2 please", 4)).isEqualTo(2);
        assertThat(VeeBookingService.parseChoice("1", 4)).isEqualTo(1);
        assertThat(VeeBookingService.parseChoice("9", 4)).isNull();
        assertThat(VeeBookingService.parseChoice("none of those", 4)).isNull();
    }

    @Test
    void googleModeOffersSlotsAndStoresState() {
        Conversation conv = conversation();
        when(scheduling.availability(tenantId)).thenReturn(google(List.of(
                Instant.parse("2026-06-09T14:00:00Z"),
                Instant.parse("2026-06-09T15:00:00Z"))));

        boolean handled = svc.handle(conv, "+18681234567", "I'd like to book a meeting");

        assertThat(handled).isTrue();
        assertThat(conv.getPendingBooking()).contains("offered").contains("2026-06-09T14:00:00Z");
        verify(conversations).save(conv);
        verify(dispatcher).enqueue(any(OutboundMessageCommand.class));
        verify(scheduling, never()).book(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void booksWhenCustomerPicksAnOfferedSlot() {
        Conversation conv = conversation();
        conv.setPendingBooking(
                "{\"offered\":[\"2026-06-09T14:00:00Z\",\"2026-06-09T15:00:00Z\"],\"reoffered\":false}");
        when(scheduling.availability(tenantId)).thenReturn(google(List.of()));

        boolean handled = svc.handle(conv, "+18681234567", "2 works for me");

        assertThat(handled).isTrue();
        verify(scheduling).book(eq(tenantId), eq(customerId), eq(conversationId),
                eq(Instant.parse("2026-06-09T15:00:00Z")), isNull(), isNull(), isNull(),
                eq(AppointmentSource.vee), isNull());
        // pending cleared
        assertThat(conv.getPendingBooking()).isNull();
        verify(conversations).save(conv);
    }

    @Test
    void linkModeSharesCalendlyLink() {
        Conversation conv = conversation();
        when(scheduling.availability(tenantId)).thenReturn(
                new AvailabilityResponse(BookingMode.LINK, 30, "UTC", "https://calendly.com/acme/intro", List.of()));

        boolean handled = svc.handle(conv, "+18681234567", "can I schedule a call?");

        assertThat(handled).isTrue();
        verify(dispatcher).enqueue(argThat((OutboundMessageCommand c) ->
                c.body().contains("https://calendly.com/acme/intro")));
        verify(conversations, never()).save(any());
    }

    @Test
    void noIntentFallsThrough() {
        Conversation conv = conversation();
        when(scheduling.availability(tenantId)).thenReturn(google(List.of(
                Instant.parse("2026-06-09T14:00:00Z"))));

        boolean handled = svc.handle(conv, "+18681234567", "thanks, that's all!");

        assertThat(handled).isFalse();
        verify(dispatcher, never()).enqueue(any());
        verify(conversations, never()).save(any());
    }

    @Test
    void disabledModeFallsThrough() {
        Conversation conv = conversation();
        when(scheduling.availability(tenantId)).thenReturn(
                new AvailabilityResponse(BookingMode.DISABLED, 30, "UTC", null, List.of()));

        assertThat(svc.handle(conv, "+18681234567", "I want to book a meeting")).isFalse();
        verify(dispatcher, never()).enqueue(any());
    }
}
