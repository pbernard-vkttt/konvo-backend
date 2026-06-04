package com.vulkantechtt.konvo.whatsapp.meta;

import com.vulkantechtt.konvo.channels.Channel;
import com.vulkantechtt.konvo.channels.ChannelRepository;
import com.vulkantechtt.konvo.common.AfterCommit;
import com.vulkantechtt.konvo.config.RabbitConfig;
import com.vulkantechtt.konvo.conversations.Conversation;
import com.vulkantechtt.konvo.conversations.ConversationRepository;
import com.vulkantechtt.konvo.conversations.ConversationStatus;
import com.vulkantechtt.konvo.conversations.Message;
import com.vulkantechtt.konvo.conversations.MessageDirection;
import com.vulkantechtt.konvo.conversations.MessageRepository;
import com.vulkantechtt.konvo.conversations.MessageStatus;
import com.vulkantechtt.konvo.customers.Customer;
import com.vulkantechtt.konvo.customers.CustomerRepository;
import com.vulkantechtt.konvo.ai.AiReplyCommand;
import com.vulkantechtt.konvo.outbox.OutboxPublisher;
import com.vulkantechtt.konvo.realtime.SseHub;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Drains {@code konvo.webhooks.inbound}: re-parses the raw Meta payload,
 * resolves the channel/tenant, upserts a {@link Customer}, opens or appends
 * to a {@link Conversation}, and persists every {@link Message}. Idempotent
 * via the {@code messages.wa_message_id} unique index — Meta retries are
 * silently swallowed.
 *
 * Status updates (delivered/read/failed) for outbound messages are also
 * processed here: we patch the matching message row in place.
 */
@Component
public class InboundIngestListener {

    private static final Logger log = LoggerFactory.getLogger(InboundIngestListener.class);

    private final ChannelRepository channels;
    private final CustomerRepository customers;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ObjectMapper json;
    private final SseHub sseHub;
    private final OutboxPublisher outbox;

    public InboundIngestListener(
            ChannelRepository channels,
            CustomerRepository customers,
            ConversationRepository conversations,
            MessageRepository messages,
            ObjectMapper json,
            SseHub sseHub,
            OutboxPublisher outbox) {
        this.channels = channels;
        this.customers = customers;
        this.conversations = conversations;
        this.messages = messages;
        this.json = json;
        this.sseHub = sseHub;
        this.outbox = outbox;
    }

    @RabbitListener(queues = RabbitConfig.WEBHOOK_QUEUE)
    @Transactional
    public void onInbound(WebhookInboundEvent event) {
        Channel channel = channels.findById(event.channelId()).orElse(null);
        if (channel == null) {
            log.warn("Dropping webhook for unknown channel={}", event.channelId());
            return;
        }

        MetaWebhookPayload payload;
        try {
            payload = json.readValue(event.rawBody(), MetaWebhookPayload.class);
        } catch (RuntimeException e) {
            log.error("Failed to parse Meta payload for channel={}", event.channelId(), e);
            return; // drop — Meta won't have a useful retry for a malformed body
        }

        if (payload.entry() == null) return;
        for (MetaWebhookPayload.Entry entry : payload.entry()) {
            if (entry.changes() == null) continue;
            for (MetaWebhookPayload.Change change : entry.changes()) {
                if (!"messages".equals(change.field()) || change.value() == null) continue;
                handleMessages(channel, change.value(), event.rawBody());
                handleStatuses(change.value());
            }
        }
    }

    private void handleMessages(Channel channel, MetaWebhookPayload.Value value, byte[] rawBody) {
        List<MetaWebhookPayload.InboundMessage> msgs = value.messages();
        if (msgs == null || msgs.isEmpty()) return;

        // Idempotency in a single query rather than one lookup per message (M-11):
        // Meta retries the whole payload, so drop any wamid already persisted.
        List<String> wamids = msgs.stream()
                .map(MetaWebhookPayload.InboundMessage::id)
                .filter(Objects::nonNull)
                .toList();
        Set<String> alreadySeen = wamids.isEmpty()
                ? Set.of()
                : messages.findByWaMessageIdIn(wamids).stream()
                        .map(Message::getWaMessageId)
                        .collect(Collectors.toSet());

        // Map each sender's wa_id to its profile name (Meta sends one contact per wa_id).
        Map<String, String> profileByWaId = new HashMap<>();
        if (value.contacts() != null) {
            for (MetaWebhookPayload.Contact c : value.contacts()) {
                if (c != null && c.wa_id() != null && c.profile() != null) {
                    profileByWaId.putIfAbsent(c.wa_id(), c.profile().name());
                }
            }
        }

        // Group the new messages by sender so the customer + conversation are
        // resolved (and created) once per sender instead of once per message.
        Map<String, List<MetaWebhookPayload.InboundMessage>> bySender = new LinkedHashMap<>();
        for (MetaWebhookPayload.InboundMessage in : msgs) {
            if (in.id() != null && alreadySeen.contains(in.id())) continue;
            bySender.computeIfAbsent(in.from(), k -> new ArrayList<>()).add(in);
        }

        String rawBodyStr = new String(rawBody, StandardCharsets.UTF_8);
        for (Map.Entry<String, List<MetaWebhookPayload.InboundMessage>> group : bySender.entrySet()) {
            ingestSenderBatch(channel, group.getKey(), group.getValue(),
                    profileByWaId.get(group.getKey()), rawBodyStr);
        }
    }

    /** Persists all of one sender's new messages against a single resolved customer/conversation. */
    private void ingestSenderBatch(Channel channel, String from,
                                   List<MetaWebhookPayload.InboundMessage> senderMsgs,
                                   String profileName, String rawBodyStr) {
        Customer customer = customers
                .findByTenantIdAndPhone(channel.getTenantId(), from)
                .orElseGet(() -> {
                    Customer c = new Customer();
                    c.setTenantId(channel.getTenantId());
                    c.setPhone(from);
                    c.setProfileName(profileName);
                    c.setDisplayName(profileName);
                    return customers.save(c);
                });

        Conversation conversation = conversations
                .findByChannelIdAndCustomerId(channel.getId(), customer.getId())
                .orElseGet(() -> {
                    Conversation c = new Conversation();
                    c.setTenantId(channel.getTenantId());
                    c.setChannelId(channel.getId());
                    c.setCustomerId(customer.getId());
                    c.setStatus(ConversationStatus.open);
                    return conversations.save(c);
                });
        if (conversation.getStatus() == ConversationStatus.closed) {
            conversation.setStatus(ConversationStatus.open);
        }

        UUID tenantId = channel.getTenantId();
        UUID conversationId = conversation.getId();
        UUID channelId = channel.getId();
        UUID customerId = customer.getId();
        boolean autoReplyEnabled = conversation.isAutoReplyEnabled();

        List<UUID> appendedMessageIds = new ArrayList<>(senderMsgs.size());
        Message latest = null;
        for (MetaWebhookPayload.InboundMessage in : senderMsgs) {
            String body = in.text() != null ? in.text().body() : null;
            Message m = new Message();
            m.setTenantId(tenantId);
            m.setConversationId(conversationId);
            m.setDirection(MessageDirection.inbound);
            m.setContentType(in.type() == null ? "text" : in.type());
            m.setBody(body);
            m.setWaMessageId(in.id());
            m.setStatus(MessageStatus.received);
            m.setSentAt(parseTimestamp(in.timestamp()));
            m.setRawPayload(rawBodyStr);
            messages.save(m);
            appendedMessageIds.add(m.getId());
            if (latest == null || m.getSentAt().isAfter(latest.getSentAt())) {
                latest = m;
            }

            log.info("Inbound message persisted channel={} conversation={} wamid={}",
                    channelId, conversationId, in.id());

            // Durable per-message AI-reply trigger written in this transaction —
            // preserves one-reply-per-inbound-message and survives a broker hiccup (H-1).
            if (autoReplyEnabled) {
                outbox.publish("ai.reply.inbound",
                        new AiReplyCommand(tenantId, conversationId, channelId, customerId, m.getId(), body));
            }
        }

        // One conversation update per batch, anchored to the freshest message.
        conversation.setLastMessageAt(latest.getSentAt());
        conversation.setLastMessagePreview(truncate(latest.getBody(), 280));
        conversations.save(conversation);

        // SSE is best-effort optimistic UI and stays post-commit.
        AfterCommit.run(() -> {
            for (UUID messageId : appendedMessageIds) {
                sseHub.broadcast(tenantId, "message_appended",
                        java.util.Map.of("conversationId", conversationId.toString(),
                                "messageId", messageId.toString()));
            }
            sseHub.broadcast(tenantId, "conversation_updated",
                    java.util.Map.of("conversationId", conversationId.toString()));
        });
    }

    private void handleStatuses(MetaWebhookPayload.Value value) {
        if (value.statuses() == null) return;
        for (MetaWebhookPayload.StatusUpdate s : value.statuses()) {
            messages.findByWaMessageId(s.id()).ifPresent(m -> {
                Instant ts = parseTimestamp(s.timestamp());
                switch (s.status()) {
                    case "sent" -> m.setStatus(MessageStatus.sent);
                    case "delivered" -> {
                        m.setStatus(MessageStatus.delivered);
                        m.setDeliveredAt(ts);
                    }
                    case "read" -> {
                        m.setStatus(MessageStatus.read);
                        m.setReadAt(ts);
                    }
                    case "failed" -> {
                        m.setStatus(MessageStatus.failed);
                        if (s.errors() != null) {
                            m.setErrorCode(s.errors().code());
                            m.setErrorMessage(s.errors().title());
                        }
                    }
                    default -> { /* unknown status — leave row as-is */ }
                }
                messages.save(m);
            });
        }
    }

    private static Instant parseTimestamp(String unixSeconds) {
        if (unixSeconds == null) return Instant.now();
        try {
            return Instant.ofEpochSecond(Long.parseLong(unixSeconds));
        } catch (NumberFormatException e) {
            return Instant.now();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

}
