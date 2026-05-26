package com.vulkantechtt.konvo.whatsapp.meta;

import com.vulkantechtt.konvo.channels.Channel;
import com.vulkantechtt.konvo.channels.ChannelRepository;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
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

    public InboundIngestListener(
            ChannelRepository channels,
            CustomerRepository customers,
            ConversationRepository conversations,
            MessageRepository messages,
            ObjectMapper json) {
        this.channels = channels;
        this.customers = customers;
        this.conversations = conversations;
        this.messages = messages;
        this.json = json;
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
        String profileName = value.contacts() != null && !value.contacts().isEmpty()
                && value.contacts().get(0).profile() != null
                ? value.contacts().get(0).profile().name()
                : null;

        for (MetaWebhookPayload.InboundMessage in : msgs) {
            if (messages.findByWaMessageId(in.id()).isPresent()) {
                continue;
            }
            Customer customer = customers
                    .findByTenantIdAndPhone(channel.getTenantId(), in.from())
                    .orElseGet(() -> {
                        Customer c = new Customer();
                        c.setTenantId(channel.getTenantId());
                        c.setPhone(in.from());
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

            String body = in.text() != null ? in.text().body() : null;
            Message m = new Message();
            m.setTenantId(channel.getTenantId());
            m.setConversationId(conversation.getId());
            m.setDirection(MessageDirection.inbound);
            m.setContentType(in.type() == null ? "text" : in.type());
            m.setBody(body);
            m.setWaMessageId(in.id());
            m.setStatus(MessageStatus.received);
            m.setSentAt(parseTimestamp(in.timestamp()));
            m.setRawPayload(new String(rawBody, StandardCharsets.UTF_8));
            messages.save(m);

            conversation.setLastMessageAt(m.getSentAt());
            conversation.setLastMessagePreview(truncate(body, 280));
            conversations.save(conversation);

            log.info("Inbound message persisted channel={} conversation={} wamid={}",
                    channel.getId(), conversation.getId(), in.id());
        }
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
