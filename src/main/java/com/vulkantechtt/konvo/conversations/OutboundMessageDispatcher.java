package com.vulkantechtt.konvo.conversations;

import com.vulkantechtt.konvo.config.RabbitConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Thin helper so callers don't have to know the routing-key shape. The actual
 * send happens asynchronously in {@link OutboundSendListener} — callers see a
 * fire-and-forget. M4 will add a sibling that also writes a {@code queued}
 * Message row inside the same caller transaction so the inbox shows the
 * pending bubble immediately.
 */
@Component
public class OutboundMessageDispatcher {

    private final RabbitTemplate rabbit;

    public OutboundMessageDispatcher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public void enqueue(OutboundMessageCommand cmd) {
        rabbit.convertAndSend(
                RabbitConfig.EVENTS_EXCHANGE,
                "whatsapp.outbound.text",
                cmd);
    }
}
