package com.example.price_tracker.task;

import com.example.price_tracker.entity.OutboxEvent;
import com.example.price_tracker.entity.OutboxEventStatus;
import com.example.price_tracker.mapper.OutboxEventMapper;
import com.example.price_tracker.mq.message.PriceAlertMessage;
import com.example.price_tracker.mq.producer.PriceAlertProducer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final List<OutboxEventStatus> READY_STATUSES = List.of(
            OutboxEventStatus.PENDING,
            OutboxEventStatus.FAILED_RETRYABLE);

    private final OutboxEventMapper outboxEventMapper;
    private final PriceAlertProducer priceAlertProducer;
    private final ObjectMapper objectMapper;

    @Value("${outbox.relay.batch-size:20}")
    private int batchSize = 20;

    @Value("${outbox.relay.max-attempts:5}")
    private int maxAttempts = 5;

    @Value("${outbox.relay.confirm-timeout-ms:5000}")
    private long confirmTimeoutMs = 5000;

    @Value("${outbox.relay.initial-backoff-seconds:5}")
    private long initialBackoffSeconds = 5;

    @Value("${outbox.relay.max-backoff-seconds:300}")
    private long maxBackoffSeconds = 300;

    @Value("${outbox.relay.enabled:true}")
    private boolean relayEnabled = true;

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:5000}")
    public void relayScheduledEvents() {
        if (!relayEnabled) {
            return;
        }
        relayPendingEvents();
    }

    public void relayPendingEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> events = outboxEventMapper.selectReadyEvents(READY_STATUSES, now, resolveBatchSize());
        for (OutboxEvent event : events) {
            relayOne(event);
        }
    }

    private int resolveBatchSize() {
        return batchSize > 0 ? batchSize : 20;
    }

    private void relayOne(OutboxEvent event) {
        PriceAlertMessage message;
        try {
            message = objectMapper.readValue(event.getPayload(), PriceAlertMessage.class);
        } catch (JsonProcessingException exception) {
            markDead(event, "invalid outbox payload: " + exception.getOriginalMessage());
            return;
        }

        String eventKey = event.getEventKey();
        try {
            message.setEventKey(eventKey);
            message.setMessageId(eventKey);
            CorrelationData correlationData = priceAlertProducer.send(message);
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
            if (priceAlertProducer.isReturned(eventKey)) {
                markDead(event, "message returned by RabbitMQ");
                return;
            }
            if (confirm.isAck()) {
                outboxEventMapper.markSent(event.getId(), LocalDateTime.now());
                log.info("outbox event published, eventKey={}, id={}", eventKey, event.getId());
                return;
            }
            markPublishFailure(event, "publisher confirm nack: " + confirm.getReason());
        } catch (Exception exception) {
            if (priceAlertProducer.isReturned(eventKey)) {
                markDead(event, "message returned by RabbitMQ");
                return;
            }
            markPublishFailure(event, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        } finally {
            priceAlertProducer.clearReturned(eventKey);
        }
    }

    private void markPublishFailure(OutboxEvent event, String error) {
        int nextAttempts = normalizeAttempts(event) + 1;
        if (nextAttempts >= maxAttempts) {
            markDead(event, error);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRetryAt = now.plusSeconds(backoffSeconds(nextAttempts));
        outboxEventMapper.markRetryable(
                event.getId(),
                nextAttempts,
                nextRetryAt,
                truncate(error),
                now);
        log.warn("outbox event publish failed, eventKey={}, id={}, attempts={}, nextRetryAt={}, error={}",
                event.getEventKey(), event.getId(), nextAttempts, nextRetryAt, error);
    }

    private void markDead(OutboxEvent event, String error) {
        int nextAttempts = normalizeAttempts(event) + 1;
        outboxEventMapper.markDead(event.getId(), nextAttempts, truncate(error), LocalDateTime.now());
        log.error("outbox event marked dead, eventKey={}, id={}, attempts={}, error={}",
                event.getEventKey(), event.getId(), nextAttempts, error);
    }

    private int normalizeAttempts(OutboxEvent event) {
        return event.getAttempts() == null ? 0 : event.getAttempts();
    }

    private long backoffSeconds(int attempts) {
        long multiplier = 1L << Math.max(0, attempts - 1);
        long backoff = initialBackoffSeconds * multiplier;
        return Math.min(backoff, maxBackoffSeconds);
    }

    private String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 1000 ? error : error.substring(0, 1000);
    }
}
