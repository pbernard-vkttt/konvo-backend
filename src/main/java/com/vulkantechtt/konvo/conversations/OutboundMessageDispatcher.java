package com.vulkantechtt.konvo.conversations;

import com.vulkantechtt.konvo.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;

/**
 * Thin helper so callers don't have to know the routing-key shape. The command
 * is written to the transactional outbox (audit H-1) rather than published to
 * Rabbit directly: when the caller is inside a transaction (e.g.
 * {@link MessageService#sendAgentReply}) the outbox row commits atomically with
 * the {@code queued} Message row, and {@link com.vulkantechtt.konvo.outbox.OutboxRelay}
 * performs the actual broker publish with retry/backoff. The real send still
 * happens asynchronously in {@link OutboundSendListener}.
 */
@Component
public class OutboundMessageDispatcher {

    static final String ROUTING_KEY = "whatsapp.outbound.text";

    private final OutboxPublisher outbox;

    public OutboundMessageDispatcher(OutboxPublisher outbox) {
        this.outbox = outbox;
    }

    public void enqueue(OutboundMessageCommand cmd) {
        outbox.publish(ROUTING_KEY, cmd);
    }
}
