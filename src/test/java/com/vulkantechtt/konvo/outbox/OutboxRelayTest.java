package com.vulkantechtt.konvo.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.conversations.OutboundMessageCommand;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock JdbcTemplate jdbc;
    @Mock RabbitTemplate rabbit;

    private final ObjectMapper mapper = JsonMapper.builder().build();

    private OutboxRelay relay() {
        return new OutboxRelay(jdbc, rabbit, mapper, new SimpleMeterRegistry(),
                50, 10, 2000, 300000);
    }

    /** Stub {@code jdbc.query(...)} to yield one due row by driving the real RowMapper. */
    private UUID stubOneDueEvent(int attempts) {
        UUID id = UUID.randomUUID();
        OutboundMessageCommand cmd = new OutboundMessageCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "+18681234567", "hi");
        String json = mapper.writeValueAsString(cmd);

        when(jdbc.query(anyString(), any(RowMapper.class), any())).thenAnswer(inv -> {
            RowMapper<?> rm = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getObject("id", UUID.class)).thenReturn(id);
            when(rs.getString("exchange")).thenReturn("konvo.events");
            when(rs.getString("routing_key")).thenReturn("whatsapp.outbound.text");
            when(rs.getString("payload_type")).thenReturn(OutboundMessageCommand.class.getName());
            when(rs.getString("payload")).thenReturn(json);
            when(rs.getInt("attempts")).thenReturn(attempts);
            return List.of(rm.mapRow(rs, 0));
        });
        return id;
    }

    @Test
    void publishesDueEventAndMarksItPublished() {
        UUID id = stubOneDueEvent(0);

        relay().drain();

        verify(rabbit).convertAndSend(eq("konvo.events"), eq("whatsapp.outbound.text"),
                any(OutboundMessageCommand.class));
        verify(jdbc).update(contains("status = 'published'"), eq(id));
    }

    @Test
    void failedPublishSchedulesABackoffRetry() {
        UUID id = stubOneDueEvent(0);
        doThrow(new RuntimeException("broker down"))
                .when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class));

        relay().drain();

        // attempts -> 1, a future next_attempt_at timestamp, error text, id.
        verify(jdbc).update(contains("next_attempt_at = ?"),
                eq(1), any(Timestamp.class), anyString(), eq(id));
    }

    @Test
    void exhaustedEventIsMarkedDead() {
        UUID id = stubOneDueEvent(9); // one below max-attempts (10); this attempt is the 10th
        doThrow(new RuntimeException("still down"))
                .when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class));

        relay().drain();

        verify(jdbc).update(contains("status = 'dead'"), eq(10), anyString(), eq(id));
    }
}
