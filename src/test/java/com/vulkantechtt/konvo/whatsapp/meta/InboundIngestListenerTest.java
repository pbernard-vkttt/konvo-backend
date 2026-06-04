package com.vulkantechtt.konvo.whatsapp.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.channels.Channel;
import com.vulkantechtt.konvo.channels.ChannelRepository;
import com.vulkantechtt.konvo.conversations.Conversation;
import com.vulkantechtt.konvo.conversations.ConversationRepository;
import com.vulkantechtt.konvo.conversations.ConversationStatus;
import com.vulkantechtt.konvo.conversations.Message;
import com.vulkantechtt.konvo.conversations.MessageRepository;
import com.vulkantechtt.konvo.conversations.MessageStatus;
import com.vulkantechtt.konvo.conversations.dto.MessageStatusUpdatedEvent;
import com.vulkantechtt.konvo.customers.Customer;
import com.vulkantechtt.konvo.customers.CustomerRepository;
import com.vulkantechtt.konvo.outbox.OutboxPublisher;
import com.vulkantechtt.konvo.realtime.SseHub;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class InboundIngestListenerTest {

    @Mock ChannelRepository channels;
    @Mock CustomerRepository customers;
    @Mock ConversationRepository conversations;
    @Mock MessageRepository messages;
    @Mock ObjectMapper json;
    @Mock SseHub sseHub;
    @Mock OutboxPublisher outbox;

    private InboundIngestListener listener;

    private final UUID channelId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private static final String FROM = "18681112222";

    @BeforeEach
    void setUp() {
        listener = new InboundIngestListener(channels, customers, conversations, messages, json, sseHub, outbox);
    }

    private MetaWebhookPayload payloadFromOneSender(String... wamids) {
        List<MetaWebhookPayload.InboundMessage> ms = new ArrayList<>();
        long ts = 1_700_000_000L;
        for (String id : wamids) {
            ms.add(new MetaWebhookPayload.InboundMessage(
                    FROM, id, String.valueOf(ts++), "text",
                    new MetaWebhookPayload.TextBody("body-" + id)));
        }
        MetaWebhookPayload.Value value = new MetaWebhookPayload.Value(
                null,
                List.of(new MetaWebhookPayload.Contact(new MetaWebhookPayload.Profile("Ada"), FROM)),
                ms,
                null);
        return new MetaWebhookPayload("whatsapp_business_account",
                List.of(new MetaWebhookPayload.Entry("waba", List.of(
                        new MetaWebhookPayload.Change("messages", value)))));
    }

    private MetaWebhookPayload payloadWithStatuses(MetaWebhookPayload.StatusUpdate... statuses) {
        MetaWebhookPayload.Value value = new MetaWebhookPayload.Value(
                null,
                null,
                null,
                List.of(statuses));
        return new MetaWebhookPayload("whatsapp_business_account",
                List.of(new MetaWebhookPayload.Entry("waba", List.of(
                        new MetaWebhookPayload.Change("messages", value)))));
    }

    private void wireChannelAndExistingThread(MetaWebhookPayload payload, byte[] raw) {
        Channel channel = new Channel();
        channel.setId(channelId);
        channel.setTenantId(tenantId);
        when(channels.findById(channelId)).thenReturn(Optional.of(channel));
        when(json.readValue(raw, MetaWebhookPayload.class)).thenReturn(payload);

        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setTenantId(tenantId);
        customer.setPhone(FROM);
        when(customers.findByTenantIdAndPhone(tenantId, FROM)).thenReturn(Optional.of(customer));

        Conversation conv = new Conversation();
        conv.setId(conversationId);
        conv.setTenantId(tenantId);
        conv.setChannelId(channelId);
        conv.setCustomerId(customerId);
        conv.setStatus(ConversationStatus.open);
        conv.setAutoReplyEnabled(true);
        when(conversations.findByChannelIdAndCustomerId(channelId, customerId)).thenReturn(Optional.of(conv));

        when(messages.save(any())).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            return m;
        });
    }

    @Test
    void resolvesCustomerAndConversationOncePerSenderForAMultiMessagePayload() {
        byte[] raw = "{}".getBytes();
        wireChannelAndExistingThread(payloadFromOneSender("wamid.A", "wamid.B"), raw);
        when(messages.findByWaMessageIdIn(anyList())).thenReturn(List.of());

        listener.onInbound(new WebhookInboundEvent(channelId, raw));

        // Two messages, one sender → exactly one customer + one conversation lookup.
        verify(customers, times(1)).findByTenantIdAndPhone(tenantId, FROM);
        verify(conversations, times(1)).findByChannelIdAndCustomerId(channelId, customerId);
        // Both messages persisted, each triggering an AI-reply (auto-reply on).
        verify(messages, times(2)).save(any());
        verify(outbox, times(2)).publish(eq("ai.reply.inbound"), any());
    }

    @Test
    void firstMessageOnANewConversationGetsAnAutoReply() {
        byte[] raw = "{}".getBytes();
        MetaWebhookPayload payload = payloadFromOneSender("wamid.first");

        Channel channel = new Channel();
        channel.setId(channelId);
        channel.setTenantId(tenantId);
        when(channels.findById(channelId)).thenReturn(Optional.of(channel));
        when(json.readValue(raw, MetaWebhookPayload.class)).thenReturn(payload);

        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setTenantId(tenantId);
        customer.setPhone(FROM);
        when(customers.findByTenantIdAndPhone(tenantId, FROM)).thenReturn(Optional.of(customer));

        // No existing thread — the listener creates one (auto-reply on by default).
        when(conversations.findByChannelIdAndCustomerId(channelId, customerId)).thenReturn(Optional.empty());
        when(conversations.save(any())).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            if (c.getId() == null) c.setId(conversationId);
            return c;
        });
        when(messages.findByWaMessageIdIn(anyList())).thenReturn(List.of());
        when(messages.save(any())).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            return m;
        });

        listener.onInbound(new WebhookInboundEvent(channelId, raw));

        // The freshly created conversation defaults to auto-reply on, so the
        // initial inbound message triggers a Vee reply.
        verify(messages, times(1)).save(any());
        verify(outbox, times(1)).publish(eq("ai.reply.inbound"), any());
    }

    @Test
    void skipsMessagesAlreadyPersisted() {
        byte[] raw = "{}".getBytes();
        wireChannelAndExistingThread(payloadFromOneSender("wamid.A", "wamid.B"), raw);
        Message seen = new Message();
        seen.setWaMessageId("wamid.A");
        when(messages.findByWaMessageIdIn(anyList())).thenReturn(List.of(seen));

        listener.onInbound(new WebhookInboundEvent(channelId, raw));

        // Only the unseen message (B) is persisted / triggers a reply.
        verify(messages, times(1)).save(any());
        verify(outbox, times(1)).publish(eq("ai.reply.inbound"), any());
    }

    @Test
    void sentStatusBroadcastsRealtimeStatusUpdate() {
        byte[] raw = "{}".getBytes();
        MetaWebhookPayload payload = payloadWithStatuses(
                new MetaWebhookPayload.StatusUpdate("wamid.out", "sent", "1700000001", FROM, null));
        wireStatusPayload(payload, raw, outboundMessage(MessageStatus.queued));
        when(messages.save(any())).thenAnswer(inv -> inv.getArgument(0));

        listener.onInbound(new WebhookInboundEvent(channelId, raw));

        MessageStatusUpdatedEvent event = capturedStatusEvent();
        assertThat(event.status()).isEqualTo(MessageStatus.sent);
        assertThat(event.messageId()).isNotNull();
    }

    @Test
    void deliveredAndReadStatusesBroadcastRealtimeTimestamps() {
        byte[] raw = "{}".getBytes();
        MetaWebhookPayload payload = payloadWithStatuses(
                new MetaWebhookPayload.StatusUpdate("wamid.out", "delivered", "1700000002", FROM, null),
                new MetaWebhookPayload.StatusUpdate("wamid.out", "read", "1700000003", FROM, null));
        wireStatusPayload(payload, raw, outboundMessage(MessageStatus.sent));
        when(messages.save(any())).thenAnswer(inv -> inv.getArgument(0));

        listener.onInbound(new WebhookInboundEvent(channelId, raw));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(sseHub, times(2)).broadcast(
                eq(tenantId),
                eq(MessageStatusUpdatedEvent.EVENT_NAME),
                payloadCaptor.capture());
        MessageStatusUpdatedEvent delivered = (MessageStatusUpdatedEvent) payloadCaptor.getAllValues().get(0);
        MessageStatusUpdatedEvent read = (MessageStatusUpdatedEvent) payloadCaptor.getAllValues().get(1);
        assertThat(delivered.status()).isEqualTo(MessageStatus.delivered);
        assertThat(delivered.deliveredAt()).isEqualTo(Instant.ofEpochSecond(1_700_000_002L));
        assertThat(read.status()).isEqualTo(MessageStatus.read);
        assertThat(read.readAt()).isEqualTo(Instant.ofEpochSecond(1_700_000_003L));
    }

    @Test
    void failedStatusBroadcastsRealtimeError() {
        byte[] raw = "{}".getBytes();
        MetaWebhookPayload payload = payloadWithStatuses(
                new MetaWebhookPayload.StatusUpdate(
                        "wamid.out",
                        "failed",
                        "1700000004",
                        FROM,
                        new MetaWebhookPayload.Errors("131000", "Message failed", "Provider rejected it")));
        wireStatusPayload(payload, raw, outboundMessage(MessageStatus.sent));
        when(messages.save(any())).thenAnswer(inv -> inv.getArgument(0));

        listener.onInbound(new WebhookInboundEvent(channelId, raw));

        MessageStatusUpdatedEvent event = capturedStatusEvent();
        assertThat(event.status()).isEqualTo(MessageStatus.failed);
        assertThat(event.errorCode()).isEqualTo("131000");
        assertThat(event.errorMessage()).isEqualTo("Message failed");
    }

    @Test
    void deliveredStatusDoesNotDowngradeReadMessage() {
        byte[] raw = "{}".getBytes();
        Message existing = outboundMessage(MessageStatus.read);
        existing.setDeliveredAt(Instant.ofEpochSecond(1_700_000_002L));
        existing.setReadAt(Instant.ofEpochSecond(1_700_000_003L));
        MetaWebhookPayload payload = payloadWithStatuses(
                new MetaWebhookPayload.StatusUpdate("wamid.out", "delivered", "1700000005", FROM, null));
        wireStatusPayload(payload, raw, existing);

        listener.onInbound(new WebhookInboundEvent(channelId, raw));

        verify(messages, never()).save(any());
        verify(sseHub, never()).broadcast(eq(tenantId), eq(MessageStatusUpdatedEvent.EVENT_NAME), any());
    }

    private void wireStatusPayload(MetaWebhookPayload payload, byte[] raw, Message existing) {
        Channel channel = new Channel();
        channel.setId(channelId);
        channel.setTenantId(tenantId);
        when(channels.findById(channelId)).thenReturn(Optional.of(channel));
        when(json.readValue(raw, MetaWebhookPayload.class)).thenReturn(payload);
        when(messages.findByWaMessageId("wamid.out")).thenReturn(Optional.of(existing));
    }

    private Message outboundMessage(MessageStatus status) {
        Message msg = new Message();
        msg.setId(UUID.randomUUID());
        msg.setTenantId(tenantId);
        msg.setConversationId(conversationId);
        msg.setWaMessageId("wamid.out");
        msg.setStatus(status);
        return msg;
    }

    private MessageStatusUpdatedEvent capturedStatusEvent() {
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(sseHub).broadcast(eq(tenantId), eq(MessageStatusUpdatedEvent.EVENT_NAME), payloadCaptor.capture());
        return (MessageStatusUpdatedEvent) payloadCaptor.getValue();
    }
}
