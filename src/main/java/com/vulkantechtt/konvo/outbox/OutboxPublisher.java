package com.vulkantechtt.konvo.outbox;

import com.vulkantechtt.konvo.config.RabbitConfig;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes an intended RabbitMQ publish as a row in the {@code outbox_events}
 * table <em>inside the caller's transaction</em>. Because the insert runs on
 * the same JDBC connection Spring binds for the surrounding JPA transaction,
 * the outbox row commits atomically with the business change (the queued
 * {@link com.vulkantechtt.konvo.conversations.Message} row, the inbound
 * message, etc.). {@link OutboxRelay} then publishes it to the broker with
 * retry/backoff, so a broker hiccup can no longer leave committed work without
 * its async follow-up (audit H-1).
 *
 * <p>All Konvo events route through the single {@link RabbitConfig#EVENTS_EXCHANGE}
 * topic exchange, so callers only supply the routing key and the command object.
 */
@Component
public class OutboxPublisher {

    private static final String INSERT = """
            insert into outbox_events
                (id, exchange, routing_key, payload_type, payload, status, attempts, next_attempt_at, created_at)
            values (?, ?, ?, ?, ?::jsonb, 'pending', 0, now(), now())
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * Enqueue {@code payload} for durable delivery to {@code routingKey} on the
     * events exchange. Must be called within an active transaction so the
     * outbox row commits (or rolls back) together with the business change;
     * when called outside one it still works but loses the atomicity guarantee.
     */
    public void publish(String routingKey, Object payload) {
        String json = objectMapper.writeValueAsString(payload);
        jdbc.update(INSERT,
                UUID.randomUUID(),
                RabbitConfig.EVENTS_EXCHANGE,
                routingKey,
                payload.getClass().getName(),
                json);
    }
}
