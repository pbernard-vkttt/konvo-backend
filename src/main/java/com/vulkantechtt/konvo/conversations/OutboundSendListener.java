package com.vulkantechtt.konvo.conversations;

import com.vulkantechtt.konvo.config.RabbitConfig;
import com.vulkantechtt.konvo.realtime.SseHub;
import com.vulkantechtt.konvo.whatsapp.WhatsAppProvider;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains {@code konvo.whatsapp.outbound}: calls the active WhatsApp provider,
 * persists the outbound Message with its wamid, and lets later status
 * webhooks patch delivered/read/failed in place.
 */
@Component
public class OutboundSendListener {

    private static final Logger log = LoggerFactory.getLogger(OutboundSendListener.class);

    private final WhatsAppProvider provider;
    private final MessageRepository messages;
    private final ConversationRepository conversations;
    private final SseHub sseHub;

    public OutboundSendListener(WhatsAppProvider provider,
                                MessageRepository messages,
                                ConversationRepository conversations,
                                SseHub sseHub) {
        this.provider = provider;
        this.messages = messages;
        this.conversations = conversations;
        this.sseHub = sseHub;
    }

    @RabbitListener(queues = RabbitConfig.OUTBOUND_SEND_QUEUE)
    @Transactional
    public void onOutbound(OutboundMessageCommand cmd) {
        Message msg = new Message();
        msg.setTenantId(cmd.tenantId());
        msg.setConversationId(cmd.conversationId());
        msg.setDirection(MessageDirection.outbound);
        msg.setContentType("text");
        msg.setBody(cmd.body());
        msg.setSentAt(Instant.now());

        try {
            WhatsAppProvider.SendResult result = provider.sendText(new WhatsAppProvider.SendTextCommand(
                    cmd.channelId(), cmd.toPhoneE164(), cmd.body(), null));
            msg.setWaMessageId(result.providerMessageId());
            msg.setStatus("sent".equals(result.status()) ? MessageStatus.sent : MessageStatus.queued);
        } catch (Exception e) {
            log.error("Outbound send failed conversation={}", cmd.conversationId(), e);
            msg.setStatus(MessageStatus.failed);
            msg.setErrorMessage(e.getMessage());
        }

        messages.save(msg);

        conversations.findById(cmd.conversationId()).ifPresent(conv -> {
            conv.setLastMessageAt(msg.getSentAt());
            conv.setLastMessagePreview(cmd.body() != null && cmd.body().length() > 280
                    ? cmd.body().substring(0, 280) : cmd.body());
            conversations.save(conv);
        });

        sseHub.broadcast(cmd.tenantId(), "message_appended",
                java.util.Map.of("conversationId", cmd.conversationId().toString(),
                        "messageId", msg.getId().toString()));
        sseHub.broadcast(cmd.tenantId(), "conversation_updated",
                java.util.Map.of("conversationId", cmd.conversationId().toString()));
    }
}
