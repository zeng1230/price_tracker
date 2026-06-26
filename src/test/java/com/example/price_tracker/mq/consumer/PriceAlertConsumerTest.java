package com.example.price_tracker.mq.consumer;

import com.example.price_tracker.mq.message.PriceAlertMessage;
import com.example.price_tracker.redis.RedisCacheService;
import com.example.price_tracker.redis.RedisKeyManager;
import com.example.price_tracker.service.NotificationService;
import com.example.price_tracker.exception.BusinessException;
import com.example.price_tracker.common.ResultCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceAlertConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private RedisCacheService cacheService;

    @InjectMocks
    private PriceAlertConsumer priceAlertConsumer;

    @Test
    void consumeDelegatesToNotificationService() {
        PriceAlertMessage message = priceAlertMessage();
        when(cacheService.setIfAbsent(idempotentKey(), "1", Duration.ofMinutes(30))).thenReturn(true);

        priceAlertConsumer.consume(message);

        verify(notificationService).consumePriceAlert(message);
    }

    @Test
    void consumeSkipsDuplicateMessageWhenIdempotentKeyAlreadyExists() {
        PriceAlertMessage message = priceAlertMessage();
        when(cacheService.setIfAbsent(idempotentKey(), "1", Duration.ofMinutes(30))).thenReturn(true, false);

        priceAlertConsumer.consume(message);
        priceAlertConsumer.consume(message);

        verify(notificationService).consumePriceAlert(message);
    }

    @Test
    void consumeAcksBySkippingWhenIdempotentKeyIsHit() {
        PriceAlertMessage message = priceAlertMessage();
        when(cacheService.setIfAbsent(idempotentKey(), "1", Duration.ofMinutes(30))).thenReturn(false);

        assertDoesNotThrow(() -> priceAlertConsumer.consume(message));

        verify(notificationService, never()).consumePriceAlert(message);
    }

    @Test
    void consumeDeletesIdempotentKeyAndRethrowsWhenNotificationHandlingFails() {
        PriceAlertMessage message = priceAlertMessage();
        when(cacheService.setIfAbsent(idempotentKey(), "1", Duration.ofMinutes(30))).thenReturn(true);
        doThrow(new IllegalStateException("boom")).when(notificationService).consumePriceAlert(message);

        assertThrows(IllegalStateException.class, () -> priceAlertConsumer.consume(message));

        verify(cacheService).delete(idempotentKey());
    }

    @Test
    void consumeDeletesIdempotentKeyAndRethrowsWhenMessageIsInvalid() {
        PriceAlertMessage message = priceAlertMessage();
        message.setProductId(null);
        when(cacheService.setIfAbsent(idempotentKey(), "1", Duration.ofMinutes(30))).thenReturn(true);
        doThrow(new BusinessException(ResultCode.BAD_REQUEST, "invalid price alert message"))
                .when(notificationService).consumePriceAlert(message);

        assertThrows(BusinessException.class, () -> priceAlertConsumer.consume(message));

        verify(cacheService).delete(idempotentKey());
    }

    private PriceAlertMessage priceAlertMessage() {
        return PriceAlertMessage.builder()
                .messageId("msg-001")
                .eventKey("TARGET_PRICE_REACHED:99:1:5:80.00:79.00:1782468930000")
                .userId(99L)
                .productId(1L)
                .watchlistId(5L)
                .productName("Laptop")
                .currentPrice(new BigDecimal("79.00"))
                .targetPrice(new BigDecimal("80.00"))
                .build();
    }

    private String idempotentKey() {
        return RedisKeyManager.notificationIdempotentKey("mq:msg-001");
    }
}
