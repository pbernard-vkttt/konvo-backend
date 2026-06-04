package com.vulkantechtt.konvo.whatsapp.meta;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
import com.vulkantechtt.konvo.customers.Customer;
import com.vulkantechtt.konvo.customers.CustomerRepository;
import com.vulkantechtt.konvo.outbox.OutboxPublisher;
import com.vulkantechtt.konvo.realtime.SseHub;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
}
