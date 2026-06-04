package com.vulkantechtt.konvo.conversations;

import com.vulkantechtt.konvo.common.AfterCommit;
import com.vulkantechtt.konvo.realtime.SseHub;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the outcome of an outbound WhatsApp send in a <em>short</em>
 * transaction, separate from the provider network call. {@link OutboundSendListener}
 * does the (retrying) provider call with no DB transaction held open, then
 * hands the result here to be written — so a slow/retrying Meta call never
 * pins a database connection (the same anti-pattern flagged for the knowledge
 * indexer in H-3).
 *
 * <p>Lives in its own bean so the {@code @Transactional} proxy actually applies
 * when the listener invokes it.
 */
@Component
public class OutboundSendWriter {

    private static final Logger log = LoggerFactory.getLogger(OutboundSendWriter.class);

    private final MessageRepository messages;
    private final ConversationRepository conversations;
    private final SseHub sseHub;

    public OutboundSendWriter(MessageRepository messages,
                              ConversationRepository conversations,
                              SseHub sseHub) {
        this.messages = messages;
        this.conversations = conversations;
        this.sseHub = sseHub;
    }

    @Transactional
    public void recordOutcome(OutboundMessageCommand cmd, OutboundSendListener.SendOutcome outcome) {
        Message msg = resolveMessage(cmd);

        if (outcome.ok()) {
            msg.setWaMessageId(outcome.providerMessageId());
            msg.setStatus("sent".equals(outcome.status()) ? MessageStatus.sent : MessageStatus.queued);
            msg.setErrorMessage(null);
        } else {
            msg.setStatus(MessageStatus.failed);
            msg.setErrorMessage(outcome.errorMessage());
        }
        messages.save(msg);

        conversations.findById(cmd.conversationId()).ifPresent(conv -> {
            conv.setLastMessageAt(msg.getSentAt());
            conv.setLastMessagePreview(cmd.body() != null && cmd.body().length() > 280
                    ? cmd.body().substring(0, 280) : cmd.body());
            conversations.save(conv);
        });

        AfterCommit.run(() -> {
            sseHub.broadcast(cmd.tenantId(), "message_appended",
                    java.util.Map.of("conversationId", cmd.conversationId().toString(),
                            "messageId", msg.getId().toString()));
            sseHub.broadcast(cmd.tenantId(), "conversation_updated",
                    java.util.Map.of("conversationId", cmd.conversationId().toString()));
        });
    }

    private Message resolveMessage(OutboundMessageCommand cmd) {
        if (cmd.messageId() != null) {
            Message existing = messages.findById(cmd.messageId()).orElse(null);
            if (existing != null && existing.getTenantId().equals(cmd.tenantId())) {
                return existing;
            }
            log.warn("Outbound command referenced missing message={} tenant={}; creating a new row",
                    cmd.messageId(), cmd.tenantId());
        }
        Message msg = new Message();
        msg.setTenantId(cmd.tenantId());
        msg.setConversationId(cmd.conversationId());
        msg.setDirection(MessageDirection.outbound);
        msg.setContentType("text");
        msg.setBody(cmd.body());
        msg.setSentAt(Instant.now());
        return msg;
    }
}
