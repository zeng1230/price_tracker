package com.example.price_tracker.mq.producer;

import com.example.price_tracker.config.RabbitMQConfig;
import com.example.price_tracker.mq.message.PriceAlertMessage;
import com.example.price_tracker.redis.RedisCacheService;
import com.example.price_tracker.metrics.PriceTrackerMetrics;
import com.example.price_tracker.redis.RedisKeyManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PriceAlertProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RedisCacheService cacheService;

    @Mock
    private PriceTrackerMetrics metrics;

    @InjectMocks
    private PriceAlertProducer producer;

    @Test
    void sendPublishesWithCorrelationDataForPublisherConfirm() {
        PriceAlertMessage message = priceAlertMessage();

        producer.send(message);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.PRICE_ALERT_EXCHANGE),
                eq(RabbitMQConfig.PRICE_ALERT_ROUTING_KEY),
                eq(message),
                any(MessagePostProcessor.class),
                any(CorrelationData.class));
    }

    @Test
    void sendDeletesProducerIdempotentKeyAndRethrowsWhenPublishingFails() {
        PriceAlertMessage message = priceAlertMessage();
        IllegalStateException exception = new IllegalStateException("publish failed");
        doThrow(exception).when(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.PRICE_ALERT_EXCHANGE),
                eq(RabbitMQConfig.PRICE_ALERT_ROUTING_KEY),
                eq(message),
                any(MessagePostProcessor.class),
                any(CorrelationData.class));

        assertThrows(IllegalStateException.class, () -> producer.send(message));

        verify(cacheService).delete(RedisKeyManager.notificationIdempotentKey("99:1:80.00"));
    }

    @Test
    void confirmAckDoesNotDeleteProducerIdempotentKey() {
        PriceAlertMessage message = priceAlertMessage();
        producer.send(message);
        ArgumentCaptor<CorrelationData> captor = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.PRICE_ALERT_EXCHANGE),
                eq(RabbitMQConfig.PRICE_ALERT_ROUTING_KEY),
                eq(message),
                any(MessagePostProcessor.class),
                captor.capture());

        producer.confirm(captor.getValue(), true, null);

        verify(cacheService, never()).delete(RedisKeyManager.notificationIdempotentKey("99:1:80.00"));
    }

    @Test
    void confirmNackDeletesProducerIdempotentKey() {
        PriceAlertMessage message = priceAlertMessage();
        producer.send(message);
        ArgumentCaptor<CorrelationData> captor = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.PRICE_ALERT_EXCHANGE),
                eq(RabbitMQConfig.PRICE_ALERT_ROUTING_KEY),
                eq(message),
                any(MessagePostProcessor.class),
                captor.capture());

        producer.confirm(captor.getValue(), false, "nack");

        verify(cacheService).delete(RedisKeyManager.notificationIdempotentKey("99:1:80.00"));
    }

    @Test
    void returnedMessageDeletesProducerIdempotentKeyAndKeepsFailureSemanticsIfConfirmAckArrivesLater() {
        PriceAlertMessage message = priceAlertMessage();
        producer.send(message);
        ArgumentCaptor<MessagePostProcessor> processorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlationCaptor = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.PRICE_ALERT_EXCHANGE),
                eq(RabbitMQConfig.PRICE_ALERT_ROUTING_KEY),
                eq(message),
                processorCaptor.capture(),
                correlationCaptor.capture());
        org.springframework.amqp.core.Message amqpMessage = new org.springframework.amqp.core.Message(
                new byte[0], new MessageProperties());
        assertDoesNotThrow(() -> processorCaptor.getValue().postProcessMessage(amqpMessage));

        producer.returnedMessage(new ReturnedMessage(
                amqpMessage,
                312,
                "NO_ROUTE",
                RabbitMQConfig.PRICE_ALERT_EXCHANGE,
                "bad.routing"));
        producer.confirm(correlationCaptor.getValue(), true, null);

        verify(cacheService).delete(RedisKeyManager.notificationIdempotentKey("99:1:80.00"));
    }

    private PriceAlertMessage priceAlertMessage() {
        return PriceAlertMessage.builder()
                .messageId("msg-001")
                .eventKey("TARGET_PRICE_REACHED:99:1:5:80.00:79.00:1782468930000")
                .userId(99L)
                .productId(1L)
                .watchlistId(5L)
                .currentPrice(new BigDecimal("79.00"))
                .targetPrice(new BigDecimal("80.00"))
                .productName("Laptop")
                .build();
    }
}
