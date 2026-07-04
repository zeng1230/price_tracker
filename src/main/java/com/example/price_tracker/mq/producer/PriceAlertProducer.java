package com.example.price_tracker.mq.producer;

import com.example.price_tracker.config.TraceIdFilter;
import com.example.price_tracker.config.RabbitMQConfig;
import com.example.price_tracker.mq.message.PriceAlertMessage;
import com.example.price_tracker.redis.RedisCacheService;
import com.example.price_tracker.redis.RedisKeyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.slf4j.MDC;
import com.example.price_tracker.metrics.PriceTrackerMetrics;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceAlertProducer implements RabbitTemplate.ConfirmCallback, RabbitTemplate.ReturnsCallback {

    private static final String HEADER_MESSAGE_ID = "X-Price-Alert-Message-Id";
    private static final String HEADER_EVENT_KEY = "X-Price-Alert-Event-Key";
    private static final String HEADER_PRODUCER_IDEMPOTENT_KEY = "X-Price-Alert-Producer-Idempotent-Key";

    private final RabbitTemplate rabbitTemplate;
    private final RedisCacheService cacheService;
    private final PriceTrackerMetrics metrics;
    private final Set<String> returnedMessageIds = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void configurePublisherCallbacks() {
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback(this);
        rabbitTemplate.setReturnsCallback(this);
    }

    public CorrelationData send(PriceAlertMessage message) {
        message.setMessageId(message.getEventKey());
        String producerIdempotentKey = buildProducerIdempotentKey(message);
        CorrelationData correlationData = new PriceAlertCorrelationData(
                message.getEventKey(),
                message.getEventKey(),
                producerIdempotentKey,
                message.getUserId(),
                message.getProductId());
        log.info(
                "Publishing price alert message, messageId={}, eventKey={}, exchange={}, routingKey={}, watchlistId={}, productId={}, userId={}, productName={}, currentPrice={}, targetPrice={}",
                message.getMessageId(),
                message.getEventKey(),
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
                        rabbitMessage.getMessageProperties().setHeader(HEADER_MESSAGE_ID, message.getMessageId());
                        rabbitMessage.getMessageProperties().setHeader(HEADER_EVENT_KEY, message.getEventKey());
                        rabbitMessage.getMessageProperties().setHeader(HEADER_PRODUCER_IDEMPOTENT_KEY, producerIdempotentKey);
                        return rabbitMessage;
                    },
                    correlationData
            );
            log.info(
                    "Published price alert message to RabbitTemplate, messageId={}, eventKey={}, routingKey={}, watchlistId={}, productId={}, userId={}, productName={}, currentPrice={}",
                    message.getMessageId(),
                    message.getEventKey(),
                    RabbitMQConfig.PRICE_ALERT_ROUTING_KEY,
                    message.getWatchlistId(),
                    message.getProductId(),
                    message.getUserId(),
                    message.getProductName(),
                    message.getCurrentPrice()
            );
            return correlationData;
        } catch (Exception ex) {
            cacheService.delete(producerIdempotentKey);
            metrics.recordPriceAlertPublish(PriceTrackerMetrics.RESULT_FAILED);
            log.error(
                    "Failed to publish price alert message synchronously, messageId={}, eventKey={}, routingKey={}, watchlistId={}, productId={}, userId={}, productName={}, currentPrice={}, targetPrice={}, decision=delete_producer_idempotent_key",
                    message.getMessageId(),
                    message.getEventKey(),
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

    public boolean isReturned(String eventKey) {
        return returnedMessageIds.contains(eventKey);
    }

    public void clearReturned(String eventKey) {
        returnedMessageIds.remove(eventKey);
    }

    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (!(correlationData instanceof PriceAlertCorrelationData priceAlertCorrelationData)) {
            log.warn("Received publisher confirm for unknown correlationData, correlationId={}, ack={}, cause={}",
                    correlationData == null ? null : correlationData.getId(), ack, cause);
            return;
        }
        if (ack) {
            metrics.recordPriceAlertPublish(PriceTrackerMetrics.RESULT_SUCCESS);
            boolean returned = returnedMessageIds.contains(priceAlertCorrelationData.messageId());
            if (returned) {
                log.warn(
                        "Publisher confirm ack received after return; return remains business delivery failure, messageId={}, eventKey={}, productId={}, userId={}",
                        priceAlertCorrelationData.messageId(),
                        priceAlertCorrelationData.eventKey(),
                        priceAlertCorrelationData.productId(),
                        priceAlertCorrelationData.userId()
                );
                return;
            }
            log.info(
                    "Publisher confirm ack, exchange accepted price alert message, messageId={}, eventKey={}, productId={}, userId={}",
                    priceAlertCorrelationData.messageId(),
                    priceAlertCorrelationData.eventKey(),
                    priceAlertCorrelationData.productId(),
                    priceAlertCorrelationData.userId()
            );
            return;
        }
        cacheService.delete(priceAlertCorrelationData.producerIdempotentKey());
        returnedMessageIds.remove(priceAlertCorrelationData.messageId());
        metrics.recordPriceAlertPublish(PriceTrackerMetrics.RESULT_FAILED);
        log.error(
                "Publisher confirm nack, messageId={}, eventKey={}, productId={}, userId={}, cause={}, decision=delete_producer_idempotent_key",
                priceAlertCorrelationData.messageId(),
                priceAlertCorrelationData.eventKey(),
                priceAlertCorrelationData.productId(),
                priceAlertCorrelationData.userId(),
                cause
        );
    }

    @Override
    public void returnedMessage(ReturnedMessage returned) {
        String messageId = readHeader(returned, HEADER_MESSAGE_ID);
        String eventKey = readHeader(returned, HEADER_EVENT_KEY);
        String producerIdempotentKey = readHeader(returned, HEADER_PRODUCER_IDEMPOTENT_KEY);
        if (StringUtils.isNotBlank(messageId)) {
            returnedMessageIds.add(messageId);
        }
        if (StringUtils.isNotBlank(producerIdempotentKey)) {
            cacheService.delete(producerIdempotentKey);
        }
        metrics.recordPriceAlertPublish(PriceTrackerMetrics.RESULT_RETURNED);
        log.error(
                "Publisher return, price alert message is unroutable, messageId={}, eventKey={}, exchange={}, routingKey={}, replyCode={}, replyText={}, decision=delete_producer_idempotent_key",
                messageId,
                eventKey,
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText()
        );
    }

    private String readHeader(ReturnedMessage returned, String headerName) {
        Object value = returned.getMessage().getMessageProperties().getHeaders().get(headerName);
        return value == null ? null : value.toString();
    }

    private String buildProducerIdempotentKey(PriceAlertMessage message) {
        return RedisKeyManager.notificationIdempotentKey(
                message.getUserId() + ":" + message.getProductId() + ":" + message.getTargetPrice());
    }

    private static final class PriceAlertCorrelationData extends CorrelationData {

        private final String messageId;
        private final String eventKey;
        private final String producerIdempotentKey;
        private final Long userId;
        private final Long productId;

        private PriceAlertCorrelationData(String messageId,
                                          String eventKey,
                                          String producerIdempotentKey,
                                          Long userId,
                                          Long productId) {
            super(messageId);
            this.messageId = messageId;
            this.eventKey = eventKey;
            this.producerIdempotentKey = producerIdempotentKey;
            this.userId = userId;
            this.productId = productId;
        }

        private String messageId() {
            return messageId;
        }

        private String eventKey() {
            return eventKey;
        }

        private String producerIdempotentKey() {
            return producerIdempotentKey;
        }

        private Long userId() {
            return userId;
        }

        private Long productId() {
            return productId;
        }
    }
}
