package com.vulkantechtt.konvo.realtime;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.Topic;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Multi-pod fan-out via Redis pub/sub. Publishes every message to a single
 * shared channel ({@code konvo.realtime}); a per-pod subscriber re-delivers
 * incoming messages to that pod's local {@link SseHub} emitters. Both
 * the publisher (this pod) and every other pod receive their copy via the
 * subscription, so there's no need to short-circuit the publisher locally.
 *
 * Wire format on the channel is one JSON object per message:
 *   { "tenantId": "...", "event": "message_appended", "payload": ... }
 */
@Component
@ConditionalOnProperty(name = "konvo.realtime.bus", havingValue = "redis")
public class RedisRealtimeBus implements RealtimeBus, MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisRealtimeBus.class);
    static final String CHANNEL = "konvo.realtime";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final SseHub hub;

    public RedisRealtimeBus(StringRedisTemplate redis,
                             ObjectMapper objectMapper,
                             @Lazy @Autowired SseHub hub) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.hub = hub;
    }

    @Override
    public void publish(UUID tenantId, String event, Object payload) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("tenantId", tenantId.toString());
            envelope.put("event", event);
            envelope.set("payload", objectMapper.valueToTree(payload));
            redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            log.error("Failed to publish realtime event {} to Redis", event, e);
            hub.deliverLocal(tenantId, event, payload);
        }
    }

    @Override
    public void onMessage(org.springframework.data.redis.connection.Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            var node = objectMapper.readTree(body);
            UUID tenantId = UUID.fromString(node.get("tenantId").asText());
            String event = node.get("event").asText();
            Object payload = objectMapper.treeToValue(node.get("payload"), Object.class);
            hub.deliverLocal(tenantId, event, payload);
        } catch (Exception e) {
            log.error("Failed to handle realtime message from Redis", e);
        }
    }

    Topic topic() {
        return new ChannelTopic(CHANNEL);
    }

    @Override
    public String name() { return "redis"; }
}
