package com.vulkantechtt.konvo.realtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Registers the pub/sub subscriber when {@code konvo.realtime.bus=redis}.
 * Kept separate from {@link RedisRealtimeBus} so the bus itself remains
 * pure-pojo testable.
 */
@Configuration
@ConditionalOnProperty(name = "konvo.realtime.bus", havingValue = "redis")
public class RedisRealtimeConfig {

    @Bean
    public RedisMessageListenerContainer realtimeListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisRealtimeBus bus) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(bus, bus.topic());
        return container;
    }
}
