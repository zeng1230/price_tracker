package com.example.price_tracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void priceAlertQueueRoutesRejectedMessagesToDeadLetterExchange() {
        Queue queue = config.priceAlertQueue();

        assertEquals(RabbitMQConfig.PRICE_ALERT_DLX,
                queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(RabbitMQConfig.PRICE_ALERT_DLQ_ROUTING_KEY,
                queue.getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    void declaresDurableDeadLetterQueueAndBinding() {
        Queue deadLetterQueue = config.priceAlertDeadLetterQueue();
        DirectExchange deadLetterExchange = config.priceAlertDeadLetterExchange();
        Binding binding = config.priceAlertDeadLetterBinding(deadLetterQueue, deadLetterExchange);

        assertEquals(RabbitMQConfig.PRICE_ALERT_DLQ, deadLetterQueue.getName());
        assertTrue(deadLetterQueue.isDurable());
        assertEquals(RabbitMQConfig.PRICE_ALERT_DLX, deadLetterExchange.getName());
        assertTrue(deadLetterExchange.isDurable());
        assertEquals(RabbitMQConfig.PRICE_ALERT_DLQ_ROUTING_KEY, binding.getRoutingKey());
    }
}
