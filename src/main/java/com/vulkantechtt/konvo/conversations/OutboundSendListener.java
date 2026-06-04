package com.vulkantechtt.konvo.conversations;

import com.vulkantechtt.konvo.config.RabbitConfig;
import com.vulkantechtt.konvo.whatsapp.TransientSendException;
import com.vulkantechtt.konvo.whatsapp.WhatsAppProvider;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Drains {@code konvo.whatsapp.outbound}: calls the active WhatsApp provider
 * and hands the result to {@link OutboundSendWriter} to persist.
 *
 * <p>Transient provider failures (429, 5xx, network) are retried in-process
 * with exponential backoff before the message is finally marked {@code failed}
 * (audit H-2). The provider call is made <em>without</em> a DB transaction
 * open, so retry backoff never pins a database connection; only the short
 * persistence step is transactional.
 *
 * <p>Unexpected (non-provider) exceptions propagate so the broker can retry and
 * ultimately dead-letter the command — see the {@code default-requeue-rejected:
 * false} + listener retry config in {@code application.yml} and the DLQ wired in
 * {@link RabbitConfig}.
 */
@Component
public class OutboundSendListener {

    private static final Logger log = LoggerFactory.getLogger(OutboundSendListener.class);

    private final WhatsAppProvider provider;
    private final OutboundSendWriter writer;
    private final MessageRepository messages;
    private final Counter sendFailures;
    private final int maxAttempts;
    private final long initialBackoffMs;

    public OutboundSendListener(WhatsAppProvider provider,
                                OutboundSendWriter writer,
                                MessageRepository messages,
                                MeterRegistry meterRegistry,
                                @Value("${konvo.whatsapp.send.max-attempts:4}") int maxAttempts,
                                @Value("${konvo.whatsapp.send.initial-backoff-ms:500}") long initialBackoffMs) {
        this.provider = provider;
        this.writer = writer;
        this.messages = messages;
        this.sendFailures = meterRegistry.counter("whatsapp.outbound.send.failures");
        this.maxAttempts = maxAttempts;
        this.initialBackoffMs = initialBackoffMs;
    }

    @RabbitListener(queues = RabbitConfig.OUTBOUND_SEND_QUEUE)
    public void onOutbound(OutboundMessageCommand cmd) {
        // Idempotency guard: a duplicate command (broker redelivery or a rare
        // outbox double-publish) must not send the same WhatsApp message twice.
        if (alreadySent(cmd)) {
            log.debug("Skipping outbound send for message={} — already in a terminal state", cmd.messageId());
            return;
        }
        SendOutcome outcome = attemptSend(cmd);
        writer.recordOutcome(cmd, outcome);
    }

    private boolean alreadySent(OutboundMessageCommand cmd) {
        if (cmd.messageId() == null) {
            return false;
        }
        return messages.findById(cmd.messageId())
                .map(Message::getStatus)
                .map(OutboundSendListener::isTerminalSent)
                .orElse(false);
    }

    private static boolean isTerminalSent(MessageStatus status) {
        return status == MessageStatus.sent
                || status == MessageStatus.delivered
                || status == MessageStatus.read;
    }

    /** Calls the provider with finite retry/backoff. No DB transaction is held. */
    private SendOutcome attemptSend(OutboundMessageCommand cmd) {
        TransientSendException lastTransient = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                WhatsAppProvider.SendResult result = provider.sendText(new WhatsAppProvider.SendTextCommand(
                        cmd.channelId(), cmd.toPhoneE164(), cmd.body(), null));
                return SendOutcome.ok(result);
            } catch (TransientSendException e) {
                sendFailures.increment();
                lastTransient = e;
                log.warn("Outbound send transient failure conversation={} attempt={}/{}: {}",
                        cmd.conversationId(), attempt, maxAttempts, e.getMessage());
                if (attempt < maxAttempts) {
                    sleepBackoff(attempt);
                }
            } catch (RuntimeException e) {
                // Permanent provider failure (rejected credentials, bad request,
                // unknown channel) — no point retrying.
                sendFailures.increment();
                log.error("Outbound send failed permanently conversation={}: {}",
                        cmd.conversationId(), e.toString());
                return SendOutcome.failed(e.getMessage());
            }
        }
        log.error("Outbound send exhausted {} attempts conversation={}", maxAttempts, cmd.conversationId());
        return SendOutcome.failed(lastTransient != null ? lastTransient.getMessage() : "send failed");
    }

    private void sleepBackoff(int attempt) {
        // Exponential backoff with jitter: initial * 2^(attempt-1) ± 20%.
        long base = initialBackoffMs * (1L << Math.min(attempt - 1, 16));
        long jitter = (long) (base * 0.2 * (ThreadLocalRandom.current().nextDouble() * 2 - 1));
        long delay = Math.max(0, base + jitter);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** Result of a (possibly retried) provider send, handed to the writer. */
    public record SendOutcome(boolean ok, String providerMessageId, String status, String errorMessage) {
        static SendOutcome ok(WhatsAppProvider.SendResult result) {
            return new SendOutcome(true, result.providerMessageId(), result.status(), null);
        }

        static SendOutcome failed(String errorMessage) {
            return new SendOutcome(false, null, null, errorMessage);
        }
    }
}
