package com.example.price_tracker.task;

import com.example.price_tracker.entity.NotificationDelivery;
import com.example.price_tracker.entity.NotificationDeliveryStatus;
import com.example.price_tracker.mapper.NotificationDeliveryMapper;
import com.example.price_tracker.metrics.PriceTrackerMetrics;
import com.example.price_tracker.notification.WebhookDeliveryClient;
import com.example.price_tracker.notification.WebhookDeliveryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDeliveryRelay {

    private static final List<NotificationDeliveryStatus> READY_STATUSES = List.of(
            NotificationDeliveryStatus.PENDING,
            NotificationDeliveryStatus.FAILED_RETRYABLE);

    private final NotificationDeliveryMapper notificationDeliveryMapper;
    private final WebhookDeliveryClient webhookDeliveryClient;
    private final PriceTrackerMetrics metrics;

    @Value("${notification.delivery.enabled:true}")
    private boolean relayEnabled = true;

    @Value("${notification.webhook.enabled:false}")
    private boolean webhookEnabled = false;

    @Value("${notification.delivery.batch-size:20}")
    private int batchSize = 20;

    @Value("${notification.delivery.max-attempts:3}")
    private int maxAttempts = 3;

    @Value("${notification.delivery.initial-backoff-seconds:5}")
    private long initialBackoffSeconds = 5;

    @Value("${notification.delivery.max-backoff-seconds:300}")
    private long maxBackoffSeconds = 300;

    @Value("${notification.delivery.claim-lease-seconds:120}")
    private long claimLeaseSeconds = 120;

    private String relayInstanceId = "notification-delivery-relay-" + UUID.randomUUID();

    @Scheduled(fixedDelayString = "${notification.delivery.fixed-delay-ms:5000}")
    public void relayScheduledDeliveries() {
        if (!relayEnabled || !webhookEnabled) {
            return;
        }
        relayPendingDeliveries();
    }

    public void relayPendingDeliveries() {
        if (!relayEnabled || !webhookEnabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int batchLimit = resolveBatchSize();
        int claimed = notificationDeliveryMapper.claimReadyDeliveries(
                READY_STATUSES,
                now,
                batchLimit,
                relayInstanceId,
                now.plusSeconds(resolveClaimLeaseSeconds()));
        if (claimed <= 0) {
            return;
        }
        List<NotificationDelivery> deliveries = notificationDeliveryMapper.selectClaimedReadyDeliveries(
                relayInstanceId,
                LocalDateTime.now(),
                batchLimit);
        for (NotificationDelivery delivery : deliveries) {
            relayOne(delivery);
        }
    }

    private void relayOne(NotificationDelivery delivery) {
        WebhookDeliveryResult result;
        try {
            result = webhookDeliveryClient.send(delivery);
        } catch (RuntimeException exception) {
            markFailure(delivery, exception.getClass().getSimpleName() + ": " + exception.getMessage(), true);
            return;
        }

        if (result.success()) {
            notificationDeliveryMapper.markSent(delivery.getId(), LocalDateTime.now());
            metrics.recordNotificationDelivery(PriceTrackerMetrics.RESULT_SUCCESS);
            log.info("notification delivery sent, id={}, eventKey={}, channel={}",
                    delivery.getId(), delivery.getEventKey(), delivery.getChannel());
            return;
        }
        markFailure(delivery, result.error(), result.retryable());
    }

    private void markFailure(NotificationDelivery delivery, String error, boolean retryable) {
        int nextAttempts = normalizeAttempts(delivery) + 1;
        if (!retryable || nextAttempts >= maxAttempts) {
            notificationDeliveryMapper.markDead(delivery.getId(), nextAttempts, truncate(error), LocalDateTime.now());
            metrics.recordNotificationDelivery("dead");
            log.warn("notification delivery marked dead, id={}, eventKey={}, attempts={}, error={}",
                    delivery.getId(), delivery.getEventKey(), nextAttempts, error);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRetryAt = now.plusSeconds(backoffSeconds(nextAttempts));
        notificationDeliveryMapper.markRetryable(delivery.getId(), nextAttempts, nextRetryAt, truncate(error), now);
        metrics.recordNotificationDelivery(PriceTrackerMetrics.RESULT_FAILED);
        log.warn("notification delivery failed, id={}, eventKey={}, attempts={}, nextRetryAt={}, error={}",
                delivery.getId(), delivery.getEventKey(), nextAttempts, nextRetryAt, error);
    }

    private int resolveBatchSize() {
        return batchSize > 0 ? batchSize : 20;
    }

    private long resolveClaimLeaseSeconds() {
        return claimLeaseSeconds > 0 ? claimLeaseSeconds : 120;
    }

    private int normalizeAttempts(NotificationDelivery delivery) {
        return delivery.getAttempts() == null ? 0 : delivery.getAttempts();
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
