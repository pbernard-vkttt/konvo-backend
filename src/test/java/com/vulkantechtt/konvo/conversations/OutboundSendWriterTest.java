package com.vulkantechtt.konvo.conversations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.conversations.dto.MessageStatusUpdatedEvent;
import com.vulkantechtt.konvo.realtime.SseHub;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboundSendWriterTest {

    @Mock MessageRepository messages;
    @Mock ConversationRepository conversations;
    @Mock SseHub sseHub;

    private OutboundSendWriter writer;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID channelId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID messageId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        writer = new OutboundSendWriter(messages, conversations, sseHub);
    }

    @Test
    void sentOutcomeBroadcastsMessageStatusUpdateForExistingQueuedMessage() {
        Message msg = queuedMessage();
        when(messages.findById(messageId)).thenReturn(Optional.of(msg));
        when(messages.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        when(conversations.findById(conversationId)).thenReturn(Optional.of(conversation()));

        writer.recordOutcome(command(), new OutboundSendListener.SendOutcome(true, "wamid.out", "sent", null));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(sseHub).broadcast(eq(tenantId), eq(MessageStatusUpdatedEvent.EVENT_NAME), payload.capture());
        MessageStatusUpdatedEvent event = (MessageStatusUpdatedEvent) payload.getValue();
        assertThat(event.messageId()).isEqualTo(messageId);
        assertThat(event.conversationId()).isEqualTo(conversationId);
        assertThat(event.status()).isEqualTo(MessageStatus.sent);
        verify(sseHub, never()).broadcast(eq(tenantId), eq("message_appended"), any());
    }

    @Test
    void failedOutcomeBroadcastsMessageStatusUpdateForExistingQueuedMessage() {
        Message msg = queuedMessage();
        when(messages.findById(messageId)).thenReturn(Optional.of(msg));
        when(messages.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        when(conversations.findById(conversationId)).thenReturn(Optional.of(conversation()));

        writer.recordOutcome(command(), new OutboundSendListener.SendOutcome(false, null, null, "Provider failed"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(sseHub).broadcast(eq(tenantId), eq(MessageStatusUpdatedEvent.EVENT_NAME), payload.capture());
        MessageStatusUpdatedEvent event = (MessageStatusUpdatedEvent) payload.getValue();
        assertThat(event.status()).isEqualTo(MessageStatus.failed);
        assertThat(event.errorMessage()).isEqualTo("Provider failed");
    }

    private OutboundMessageCommand command() {
        return new OutboundMessageCommand(
                messageId,
                tenantId,
                conversationId,
                channelId,
                customerId,
                "18681112222",
                "Hello",
                SenderType.agent,
                "Test Agent");
    }

    private Message queuedMessage() {
        Message msg = new Message();
        msg.setId(messageId);
        msg.setTenantId(tenantId);
        msg.setConversationId(conversationId);
        msg.setDirection(MessageDirection.outbound);
        msg.setContentType("text");
        msg.setBody("Hello");
        msg.setStatus(MessageStatus.queued);
        msg.setSentAt(Instant.parse("2026-06-04T10:00:00Z"));
        return msg;
    }

    private Conversation conversation() {
        Conversation conv = new Conversation();
        conv.setId(conversationId);
        conv.setTenantId(tenantId);
        conv.setChannelId(channelId);
        conv.setCustomerId(customerId);
        return conv;
    }
}
