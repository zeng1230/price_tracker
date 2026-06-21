package com.example.price_tracker.mq.producer;

import com.example.price_tracker.config.RabbitMQConfig;
import com.example.price_tracker.mq.message.PriceAlertMessage;
import com.example.price_tracker.redis.RedisCacheService;
import com.example.price_tracker.redis.RedisKeyManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PriceAlertProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RedisCacheService cacheService;

    @InjectMocks
    private PriceAlertProducer producer;

    @Test
    void sendDeletesProducerIdempotentKeyAndRethrowsWhenPublishingFails() {
        PriceAlertMessage message = PriceAlertMessage.builder()
                .messageId("msg-001")
                .userId(99L)
                .productId(1L)
                .watchlistId(5L)
                .currentPrice(new BigDecimal("79.00"))
                .targetPrice(new BigDecimal("80.00"))
                .productName("Laptop")
                .build();
        IllegalStateException exception = new IllegalStateException("publish failed");
        doThrow(exception).when(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.PRICE_ALERT_EXCHANGE),
                eq(RabbitMQConfig.PRICE_ALERT_ROUTING_KEY),
                eq(message),
                any(MessagePostProcessor.class));

        assertThrows(IllegalStateException.class, () -> producer.send(message));

        verify(cacheService).delete(RedisKeyManager.notificationIdempotentKey("99:1:80.00"));
    }
}
