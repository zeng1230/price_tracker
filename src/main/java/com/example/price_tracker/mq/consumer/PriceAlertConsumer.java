package com.example.price_tracker.mq.consumer;

import com.example.price_tracker.config.TraceIdFilter;
import com.example.price_tracker.config.RabbitMQConfig;
import com.example.price_tracker.mq.message.PriceAlertMessage;
import com.example.price_tracker.redis.RedisCacheService;
import com.example.price_tracker.redis.RedisKeyManager;
import com.example.price_tracker.metrics.PriceTrackerMetrics;
import com.example.price_tracker.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceAlertConsumer {

    private final NotificationService notificationService;
    private final RedisCacheService cacheService;
    private final PriceTrackerMetrics metrics;

    @Value("${notification.consumer-idempotent.ttl-minutes:30}")
    private long consumerIdempotentTtlMinutes = 30;

    public void consume(PriceAlertMessage message) {
        consume(message, null);
    }

    @RabbitListener(queues = RabbitMQConfig.PRICE_ALERT_QUEUE)
    public void consume(PriceAlertMessage message,
                        @Header(name = TraceIdFilter.TRACE_ID_HEADER, required = false) String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TraceIdFilter.TRACE_ID_MDC_KEY, traceId);
        }
        try {
            consumeInternal(message);
        } finally {
            if (traceId != null && !traceId.isBlank()) {
                MDC.remove(TraceIdFilter.TRACE_ID_MDC_KEY);
            }
        }
    }

    private void consumeInternal(PriceAlertMessage message) {
        log.info(
                "Received price alert message from queue={}, messageId={}, watchlistId={}, productId={}, userId={}, currentPrice={}, targetPrice={}",
                RabbitMQConfig.PRICE_ALERT_QUEUE,
                message == null ? null : message.getMessageId(),
                message == null ? null : message.getWatchlistId(),
                message == null ? null : message.getProductId(),
                message == null ? null : message.getUserId(),
                message == null ? null : message.getCurrentPrice(),
                message == null ? null : message.getTargetPrice()
        );
        String idempotentKey = buildIdempotentKey(message);
        boolean acquired = cacheService.setIfAbsent(
                idempotentKey,
                "1",
                Duration.ofMinutes(consumerIdempotentTtlMinutes));
        if (!acquired) {
            metrics.recordPriceAlertConsume(PriceTrackerMetrics.RESULT_DUPLICATE);
            log.info(
                    "Idempotent hit for price alert message, key={}, messageId={}, watchlistId={}, productId={}, userId={}, decision=ack_skip",
                    idempotentKey,
                    message == null ? null : message.getMessageId(),
                    message == null ? null : message.getWatchlistId(),
                    message == null ? null : message.getProductId(),
                    message == null ? null : message.getUserId()
            );
            return;
        }
        try {
            log.info(
                    "Start processing price alert message, key={}, messageId={}, watchlistId={}, productId={}, userId={}",
                    idempotentKey,
                    message == null ? null : message.getMessageId(),
                    message == null ? null : message.getWatchlistId(),
                    message == null ? null : message.getProductId(),
                    message == null ? null : message.getUserId()
            );
            notificationService.consumePriceAlert(message);
            metrics.recordPriceAlertConsume(PriceTrackerMetrics.RESULT_SUCCESS);
            log.info(
                    "Notification send success, key={}, messageId={}, watchlistId={}, productId={}, userId={}",
                    idempotentKey,
                    message == null ? null : message.getMessageId(),
                    message == null ? null : message.getWatchlistId(),
                    message == null ? null : message.getProductId(),
                    message == null ? null : message.getUserId()
            );
        } catch (Exception ex) {
            cacheService.delete(idempotentKey);
            metrics.recordPriceAlertConsume(PriceTrackerMetrics.RESULT_FAILED);
            log.error(
                    "Notification send failed, key={}, messageId={}, watchlistId={}, productId={}, userId={}, decision=delete_idempotent_key_and_rethrow",
                    idempotentKey,
                    message == null ? null : message.getMessageId(),
                    message == null ? null : message.getWatchlistId(),
                    message == null ? null : message.getProductId(),
                    message == null ? null : message.getUserId(),
                    ex
            );
            throw ex;
        }
    }

    private String buildIdempotentKey(PriceAlertMessage message) {
        if (message == null) {
            return RedisKeyManager.notificationIdempotentKey("mq:null");
        }
        if (message.getMessageId() != null && !message.getMessageId().isBlank()) {
            return RedisKeyManager.notificationIdempotentKey("mq:" + message.getMessageId());
        }
        return RedisKeyManager.notificationIdempotentKey(
                "mq:"
                        + message.getUserId()
                        + ":"
                        + message.getProductId()
                        + ":"
                        + message.getTargetPrice()
                        + ":"
                        + message.getCurrentPrice()
                        + ":"
                        + message.getTriggeredAt()
        );
    }
}
