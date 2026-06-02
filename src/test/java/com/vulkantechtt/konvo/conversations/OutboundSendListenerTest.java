package com.vulkantechtt.konvo.conversations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.realtime.SseHub;
import com.vulkantechtt.konvo.whatsapp.WhatsAppProvider;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboundSendListenerTest {

    @Mock WhatsAppProvider provider;
    @Mock MessageRepository messages;
    @Mock ConversationRepository conversations;
    @Mock SseHub sseHub;

    @Test
    void updatesQueuedMessageReferencedByCommand() {
        UUID messageId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        Message queued = new Message();
        queued.setId(messageId);
        queued.setTenantId(tenantId);
        queued.setConversationId(conversationId);
        queued.setDirection(MessageDirection.outbound);
        queued.setContentType("text");
        queued.setBody("hello");
        queued.setStatus(MessageStatus.queued);

        when(messages.findById(messageId)).thenReturn(Optional.of(queued));
        when(conversations.findById(conversationId)).thenReturn(Optional.empty());
        when(provider.sendText(any())).thenReturn(new WhatsAppProvider.SendResult("wamid.123", "sent"));

        OutboundSendListener listener = new OutboundSendListener(provider, messages, conversations, sseHub);
        listener.onOutbound(new OutboundMessageCommand(
                messageId,
                tenantId,
                conversationId,
                channelId,
                customerId,
                "+18681234567",
                "hello"));

        assertThat(queued.getWaMessageId()).isEqualTo("wamid.123");
        assertThat(queued.getStatus()).isEqualTo(MessageStatus.sent);
        verify(messages).save(queued);

        ArgumentCaptor<Map<String, String>> payload = ArgumentCaptor.forClass(Map.class);
        verify(sseHub).broadcast(eq(tenantId), eq("message_appended"), payload.capture());
        assertThat(payload.getValue()).containsEntry("messageId", messageId.toString());
    }
}
