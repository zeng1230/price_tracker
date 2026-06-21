package com.example.price_tracker.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@EnableRabbit
@Configuration
public class RabbitMQConfig {

    public static final String PRICE_ALERT_EXCHANGE = "price.alert.exchange";
    public static final String PRICE_ALERT_QUEUE = "price.alert.queue";
    public static final String PRICE_ALERT_ROUTING_KEY = "price.alert";
    public static final String PRICE_ALERT_DLX = "price.alert.dlx";
    public static final String PRICE_ALERT_DLQ = "price.alert.dlq";
    public static final String PRICE_ALERT_DLQ_ROUTING_KEY = "price.alert.dlq";

    @Bean
    public DirectExchange priceAlertExchange() {
        return new DirectExchange(PRICE_ALERT_EXCHANGE, true, false);
    }

    @Bean
    public Queue priceAlertQueue() {
        return QueueBuilder.durable(PRICE_ALERT_QUEUE)
                .deadLetterExchange(PRICE_ALERT_DLX)
                .deadLetterRoutingKey(PRICE_ALERT_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding priceAlertBinding(@Qualifier("priceAlertQueue") Queue priceAlertQueue,
                                     @Qualifier("priceAlertExchange") DirectExchange priceAlertExchange) {
        return BindingBuilder.bind(priceAlertQueue).to(priceAlertExchange).with(PRICE_ALERT_ROUTING_KEY);
    }

    @Bean
    public DirectExchange priceAlertDeadLetterExchange() {
        return new DirectExchange(PRICE_ALERT_DLX, true, false);
    }

    @Bean
    public Queue priceAlertDeadLetterQueue() {
        return QueueBuilder.durable(PRICE_ALERT_DLQ).build();
    }

    @Bean
    public Binding priceAlertDeadLetterBinding(
            @Qualifier("priceAlertDeadLetterQueue") Queue priceAlertDeadLetterQueue,
            @Qualifier("priceAlertDeadLetterExchange") DirectExchange priceAlertDeadLetterExchange) {
        return BindingBuilder.bind(priceAlertDeadLetterQueue)
                .to(priceAlertDeadLetterExchange)
                .with(PRICE_ALERT_DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
