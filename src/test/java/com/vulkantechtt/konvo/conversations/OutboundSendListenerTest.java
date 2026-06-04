package com.vulkantechtt.konvo.conversations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.whatsapp.TransientSendException;
import com.vulkantechtt.konvo.whatsapp.WhatsAppProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
    @Mock OutboundSendWriter writer;
    @Mock MessageRepository messages;

    private OutboundSendListener newListener() {
        // 4 attempts, 1ms base backoff so the retry tests stay fast.
        return new OutboundSendListener(provider, writer, messages, new SimpleMeterRegistry(), 4, 1);
    }

    private OutboundMessageCommand command(UUID messageId) {
        return new OutboundMessageCommand(
                messageId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "+18681234567", "hello");
    }

    @Test
    void successfulSendRecordsSentOutcome() {
        OutboundMessageCommand cmd = command(null);
        when(provider.sendText(any())).thenReturn(new WhatsAppProvider.SendResult("wamid.123", "sent"));

        newListener().onOutbound(cmd);

        ArgumentCaptor<OutboundSendListener.SendOutcome> outcome =
                ArgumentCaptor.forClass(OutboundSendListener.SendOutcome.class);
        verify(writer).recordOutcome(any(), outcome.capture());
        assertThat(outcome.getValue().ok()).isTrue();
        assertThat(outcome.getValue().providerMessageId()).isEqualTo("wamid.123");
        assertThat(outcome.getValue().status()).isEqualTo("sent");
    }

    @Test
    void retriesTransientFailuresThenSucceeds() {
        OutboundMessageCommand cmd = command(null);
        when(provider.sendText(any()))
                .thenThrow(new TransientSendException("429", null))
                .thenThrow(new TransientSendException("503", null))
                .thenReturn(new WhatsAppProvider.SendResult("wamid.ok", "sent"));

        newListener().onOutbound(cmd);

        verify(provider, times(3)).sendText(any());
        ArgumentCaptor<OutboundSendListener.SendOutcome> outcome =
                ArgumentCaptor.forClass(OutboundSendListener.SendOutcome.class);
        verify(writer).recordOutcome(any(), outcome.capture());
        assertThat(outcome.getValue().ok()).isTrue();
    }

    @Test
    void exhaustsRetriesThenRecordsFailure() {
        OutboundMessageCommand cmd = command(null);
        when(provider.sendText(any())).thenThrow(new TransientSendException("still down", null));

        newListener().onOutbound(cmd);

        verify(provider, times(4)).sendText(any()); // maxAttempts
        ArgumentCaptor<OutboundSendListener.SendOutcome> outcome =
                ArgumentCaptor.forClass(OutboundSendListener.SendOutcome.class);
        verify(writer).recordOutcome(any(), outcome.capture());
        assertThat(outcome.getValue().ok()).isFalse();
    }

    @Test
    void permanentFailureIsNotRetried() {
        OutboundMessageCommand cmd = command(null);
        when(provider.sendText(any()))
                .thenThrow(new IllegalStateException("credentials rejected"));

        newListener().onOutbound(cmd);

        verify(provider, times(1)).sendText(any()); // no retry on a permanent error
        ArgumentCaptor<OutboundSendListener.SendOutcome> outcome =
                ArgumentCaptor.forClass(OutboundSendListener.SendOutcome.class);
        verify(writer).recordOutcome(any(), outcome.capture());
        assertThat(outcome.getValue().ok()).isFalse();
    }

    @Test
    void skipsSendWhenMessageAlreadyInTerminalState() {
        UUID messageId = UUID.randomUUID();
        Message alreadySent = new Message();
        alreadySent.setId(messageId);
        alreadySent.setStatus(MessageStatus.delivered);
        when(messages.findById(messageId)).thenReturn(Optional.of(alreadySent));

        newListener().onOutbound(command(messageId));

        verifyNoInteractions(provider);
        verify(writer, never()).recordOutcome(any(), any());
    }
}
