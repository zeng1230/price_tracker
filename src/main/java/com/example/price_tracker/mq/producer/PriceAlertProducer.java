package com.example.price_tracker.mq.producer;

import com.example.price_tracker.config.TraceIdFilter;
import com.example.price_tracker.config.RabbitMQConfig;
import com.example.price_tracker.mq.message.PriceAlertMessage;
import com.example.price_tracker.redis.RedisCacheService;
import com.example.price_tracker.redis.RedisKeyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceAlertProducer {

    private final RabbitTemplate rabbitTemplate;
    private final RedisCacheService cacheService;

    public void send(PriceAlertMessage message) {
        log.info(
                "Publishing price alert message, messageId={}, exchange={}, routingKey={}, watchlistId={}, productId={}, userId={}, productName={}, currentPrice={}, targetPrice={}",
                message.getMessageId(),
                RabbitMQConfig.PRICE_ALERT_EXCHANGE,
                RabbitMQConfig.PRICE_ALERT_ROUTING_KEY,
                message.getWatchlistId(),
                message.getProductId(),
                message.getUserId(),
                message.getProductName(),
                message.getCurrentPrice(),
                message.getTargetPrice()
        );
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.PRICE_ALERT_EXCHANGE,
                    RabbitMQConfig.PRICE_ALERT_ROUTING_KEY,
                    message,
                    rabbitMessage -> {
                        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
                        if (StringUtils.isNotBlank(traceId)) {
                            rabbitMessage.getMessageProperties()
                                    .setHeader(TraceIdFilter.TRACE_ID_HEADER, traceId);
                        }
                        return rabbitMessage;
                    }
            );
            log.info(
                    "Published price alert message successfully, messageId={}, routingKey={}, watchlistId={}, productId={}, userId={}, productName={}, currentPrice={}",
                    message.getMessageId(),
                    RabbitMQConfig.PRICE_ALERT_ROUTING_KEY,
                    message.getWatchlistId(),
                    message.getProductId(),
                    message.getUserId(),
                    message.getProductName(),
                    message.getCurrentPrice()
            );
        } catch (Exception ex) {
            cacheService.delete(buildProducerIdempotentKey(message));
            log.error(
                    "Failed to publish price alert message, messageId={}, routingKey={}, watchlistId={}, productId={}, userId={}, productName={}, currentPrice={}, targetPrice={}",
                    message.getMessageId(),
                    RabbitMQConfig.PRICE_ALERT_ROUTING_KEY,
                    message.getWatchlistId(),
                    message.getProductId(),
                    message.getUserId(),
                    message.getProductName(),
                    message.getCurrentPrice(),
                    message.getTargetPrice(),
                    ex
            );
            throw ex;
        }
    }

    private String buildProducerIdempotentKey(PriceAlertMessage message) {
        return RedisKeyManager.notificationIdempotentKey(
                message.getUserId() + ":" + message.getProductId() + ":" + message.getTargetPrice());
    }
}
