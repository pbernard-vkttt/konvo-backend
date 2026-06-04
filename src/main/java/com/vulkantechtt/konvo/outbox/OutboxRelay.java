package com.vulkantechtt.konvo.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Drains {@code outbox_events}: every poll it claims the due, still-pending
 * rows and publishes each to the events exchange via the same
 * {@link RabbitTemplate} (and Jackson converter) the direct callers used, so
 * downstream {@code @RabbitListener}s deserialise the command unchanged.
 *
 * <p>On a publish failure the row is kept {@code pending} with an exponential
 * backoff on {@code next_attempt_at}; after {@code max-attempts} it is marked
 * {@code dead} for operator attention. ShedLock guarantees a single relay
 * runner across a multi-pod deploy, mirroring {@code UsageRollupJob}.
 *
 * <p>Latency note: in the happy path a row is published within one poll
 * interval (default 1s) of commit. The producing request's optimistic SSE
 * still fires immediately (it stays in its own after-commit callback); only
 * the durable Rabbit hand-off waits for the relay.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    /** Only ever reconstruct our own command records — never arbitrary classes. */
    private static final String TRUSTED_PACKAGE = "com.vulkantechtt.konvo.";

    private static final String SELECT_DUE = """
            select id, exchange, routing_key, payload_type, payload, attempts
            from outbox_events
            where status = 'pending' and next_attempt_at <= now()
            order by created_at asc
            limit ?
            """;

    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final ObjectMapper objectMapper;
    private final Counter publishFailures;
    private final Counter deadEvents;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration baseBackoff;
    private final Duration maxBackoff;

    public OutboxRelay(JdbcTemplate jdbc,
                       RabbitTemplate rabbit,
                       ObjectMapper objectMapper,
                       MeterRegistry meterRegistry,
                       @Value("${konvo.outbox.batch-size:50}") int batchSize,
                       @Value("${konvo.outbox.max-attempts:10}") int maxAttempts,
                       @Value("${konvo.outbox.base-backoff-ms:2000}") long baseBackoffMs,
                       @Value("${konvo.outbox.max-backoff-ms:300000}") long maxBackoffMs) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.objectMapper = objectMapper;
        this.publishFailures = meterRegistry.counter("outbox.publish.failures");
        this.deadEvents = meterRegistry.counter("outbox.events.dead");
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.baseBackoff = Duration.ofMillis(baseBackoffMs);
        this.maxBackoff = Duration.ofMillis(maxBackoffMs);
    }

    @Scheduled(fixedDelayString = "${konvo.outbox.poll-ms:1000}")
    @SchedulerLock(name = "outbox-relay", lockAtMostFor = "PT2M")
    @Transactional
    public void drain() {
        List<DueEvent> due = jdbc.query(SELECT_DUE,
                (rs, i) -> new DueEvent(
                        rs.getObject("id", UUID.class),
                        rs.getString("exchange"),
                        rs.getString("routing_key"),
                        rs.getString("payload_type"),
                        rs.getString("payload"),
                        rs.getInt("attempts")),
                batchSize);

        for (DueEvent e : due) {
            try {
                Object command = deserialize(e);
                rabbit.convertAndSend(e.exchange(), e.routingKey(), command);
                jdbc.update("update outbox_events set status = 'published', published_at = now() where id = ?",
                        e.id());
            } catch (Exception ex) {
                handleFailure(e, ex);
            }
        }
    }

    private void handleFailure(DueEvent e, Exception ex) {
        publishFailures.increment();
        int attempts = e.attempts() + 1;
        String error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        if (attempts >= maxAttempts) {
            deadEvents.increment();
            log.error("Outbox event {} ({}) exhausted {} attempts — marking dead",
                    e.id(), e.routingKey(), attempts, ex);
            jdbc.update("update outbox_events set status = 'dead', attempts = ?, last_error = ? where id = ?",
                    attempts, error, e.id());
        } else {
            Instant next = Instant.now().plus(backoff(attempts));
            log.warn("Outbox publish failed for {} ({}) attempt {} — retrying after {}: {}",
                    e.id(), e.routingKey(), attempts, next, ex.toString());
            jdbc.update("update outbox_events set attempts = ?, next_attempt_at = ?, last_error = ? where id = ?",
                    attempts, java.sql.Timestamp.from(next), error, e.id());
        }
    }

    /** Exponential backoff capped at {@code maxBackoff}: base * 2^(attempts-1). */
    private Duration backoff(int attempts) {
        long millis = baseBackoff.toMillis() * (1L << Math.min(attempts - 1, 16));
        long capped = Math.min(millis, maxBackoff.toMillis());
        return Duration.ofMillis(capped);
    }

    private Object deserialize(DueEvent e) throws ClassNotFoundException {
        if (!e.payloadType().startsWith(TRUSTED_PACKAGE)) {
            throw new IllegalStateException("Refusing to deserialise untrusted outbox type " + e.payloadType());
        }
        Class<?> type = Class.forName(e.payloadType());
        return objectMapper.readValue(e.payload(), type);
    }

    private record DueEvent(UUID id, String exchange, String routingKey,
                            String payloadType, String payload, int attempts) {}
}
