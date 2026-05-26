package com.vulkantechtt.konvo.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "konvo.events";
    public static final String WEBHOOK_QUEUE = "konvo.webhooks.inbound";
    public static final String AI_REPLY_QUEUE = "konvo.ai.reply";
    public static final String OUTBOUND_SEND_QUEUE = "konvo.whatsapp.outbound";
    public static final String DLQ = "konvo.deadletter";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Queue webhookQueue() {
        return QueueBuilder.durable(WEBHOOK_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DLQ)
                .build();
    }

    @Bean
    public Queue aiReplyQueue() {
        return QueueBuilder.durable(AI_REPLY_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DLQ)
                .build();
    }

    @Bean
    public Queue outboundSendQueue() {
        return QueueBuilder.durable(OUTBOUND_SEND_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DLQ)
                .build();
    }

    @Bean
    public Binding webhookBinding(Queue webhookQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(webhookQueue).to(eventsExchange).with("webhook.inbound.#");
    }

    @Bean
    public Binding aiReplyBinding(Queue aiReplyQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(aiReplyQueue).to(eventsExchange).with("ai.reply.#");
    }

    @Bean
    public Binding outboundSendBinding(Queue outboundSendQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(outboundSendQueue).to(eventsExchange).with("whatsapp.outbound.#");
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
