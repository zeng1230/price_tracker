package com.example.price_tracker.task;

import com.example.price_tracker.entity.NotificationDelivery;
import com.example.price_tracker.entity.NotificationDeliveryStatus;
import com.example.price_tracker.mapper.NotificationDeliveryMapper;
import com.example.price_tracker.metrics.PriceTrackerMetrics;
import com.example.price_tracker.notification.WebhookDeliveryClient;
import com.example.price_tracker.notification.WebhookDeliveryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryRelayTest {

    @Mock
    private NotificationDeliveryMapper notificationDeliveryMapper;

    @Mock
    private WebhookDeliveryClient webhookDeliveryClient;

    @Mock
    private PriceTrackerMetrics metrics;

    private NotificationDeliveryRelay relay;

    @BeforeEach
    void setUp() {
        relay = new NotificationDeliveryRelay(notificationDeliveryMapper, webhookDeliveryClient, metrics);
        ReflectionTestUtils.setField(relay, "batchSize", 20);
        ReflectionTestUtils.setField(relay, "maxAttempts", 3);
        ReflectionTestUtils.setField(relay, "initialBackoffSeconds", 5L);
        ReflectionTestUtils.setField(relay, "maxBackoffSeconds", 300L);
        ReflectionTestUtils.setField(relay, "claimLeaseSeconds", 120L);
        ReflectionTestUtils.setField(relay, "relayInstanceId", "test-delivery-relay");
        ReflectionTestUtils.setField(relay, "relayEnabled", true);
        ReflectionTestUtils.setField(relay, "webhookEnabled", true);
    }

    @Test
    void relayMarksDeliverySentWhenWebhookSucceeds() {
        NotificationDelivery delivery = delivery(1L, 0);
        when(notificationDeliveryMapper.claimReadyDeliveries(any(), any(LocalDateTime.class), eq(20), eq("test-delivery-relay"), any(LocalDateTime.class)))
                .thenReturn(1);
        when(notificationDeliveryMapper.selectClaimedReadyDeliveries(eq("test-delivery-relay"), any(LocalDateTime.class), eq(20)))
                .thenReturn(List.of(delivery));
        when(webhookDeliveryClient.send(delivery)).thenReturn(WebhookDeliveryResult.success(202));

        relay.relayPendingDeliveries();

        verify(notificationDeliveryMapper).markSent(eq(1L), any(LocalDateTime.class));
    }

    @Test
    void relayMarksClientErrorDead() {
        NotificationDelivery delivery = delivery(2L, 0);
        when(notificationDeliveryMapper.claimReadyDeliveries(any(), any(LocalDateTime.class), eq(20), eq("test-delivery-relay"), any(LocalDateTime.class)))
                .thenReturn(1);
        when(notificationDeliveryMapper.selectClaimedReadyDeliveries(eq("test-delivery-relay"), any(LocalDateTime.class), eq(20)))
                .thenReturn(List.of(delivery));
        when(webhookDeliveryClient.send(delivery)).thenReturn(WebhookDeliveryResult.dead("webhook client error status=400"));

        relay.relayPendingDeliveries();

        verify(notificationDeliveryMapper).markDead(eq(2L), eq(1), eq("webhook client error status=400"), any(LocalDateTime.class));
    }

    @Test
    void relayRetriesServerErrorWithBackoff() {
        NotificationDelivery delivery = delivery(3L, 1);
        when(notificationDeliveryMapper.claimReadyDeliveries(any(), any(LocalDateTime.class), eq(20), eq("test-delivery-relay"), any(LocalDateTime.class)))
                .thenReturn(1);
        when(notificationDeliveryMapper.selectClaimedReadyDeliveries(eq("test-delivery-relay"), any(LocalDateTime.class), eq(20)))
                .thenReturn(List.of(delivery));
        when(webhookDeliveryClient.send(delivery)).thenReturn(WebhookDeliveryResult.retryable("webhook server error status=500"));

        relay.relayPendingDeliveries();

        ArgumentCaptor<LocalDateTime> retryAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notificationDeliveryMapper).markRetryable(eq(3L), eq(2), retryAtCaptor.capture(), eq("webhook server error status=500"), any(LocalDateTime.class));
        assertThat(retryAtCaptor.getValue()).isAfter(LocalDateTime.now());
    }

    @Test
    void relayDoesNotSendWhenNoDeliveriesClaimed() {
        when(notificationDeliveryMapper.claimReadyDeliveries(any(), any(LocalDateTime.class), eq(20), eq("test-delivery-relay"), any(LocalDateTime.class)))
                .thenReturn(0);

        relay.relayPendingDeliveries();

        verify(notificationDeliveryMapper, never()).selectClaimedReadyDeliveries(any(), any(), any(Integer.class));
        verify(webhookDeliveryClient, never()).send(any());
    }

    private NotificationDelivery delivery(Long id, int attempts) {
        return NotificationDelivery.builder()
                .id(id)
                .eventKey("event-" + id)
                .channel("WEBHOOK")
                .payload("{\"eventKey\":\"event-" + id + "\"}")
                .status(NotificationDeliveryStatus.PENDING)
                .attempts(attempts)
                .nextRetryAt(LocalDateTime.now().minusSeconds(1))
                .build();
    }
}
