package com.vulkantechtt.konvo.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.vulkantechtt.konvo.conversations.OutboundMessageCommand;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock JdbcTemplate jdbc;

    @Test
    void writesPendingRowWithSerialisedPayloadAndType() {
        OutboxPublisher publisher = new OutboxPublisher(jdbc, JsonMapper.builder().build());
        OutboundMessageCommand cmd = new OutboundMessageCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "+18681234567", "hello there");

        publisher.publish("whatsapp.outbound.text", cmd);

        // One matcher per actual arg: sql, id, exchange, routing_key, type, payload.
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(
                anyString(),
                any(UUID.class),
                eq("konvo.events"),
                eq("whatsapp.outbound.text"),
                eq(OutboundMessageCommand.class.getName()),
                payload.capture());
        assertThat(payload.getValue()).contains("+18681234567").contains("hello there");
    }
}
