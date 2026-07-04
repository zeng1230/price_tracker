package com.example.price_tracker.task;

import com.example.price_tracker.entity.OutboxEvent;
import com.example.price_tracker.entity.OutboxEventStatus;
import com.example.price_tracker.mapper.OutboxEventMapper;
import com.example.price_tracker.mq.message.PriceAlertMessage;
import com.example.price_tracker.mq.producer.PriceAlertProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventMapper outboxEventMapper;

    @Mock
    private PriceAlertProducer priceAlertProducer;

    private OutboxRelay outboxRelay;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        outboxRelay = new OutboxRelay(outboxEventMapper, priceAlertProducer, objectMapper);
        ReflectionTestUtils.setField(outboxRelay, "batchSize", 20);
        ReflectionTestUtils.setField(outboxRelay, "maxAttempts", 5);
        ReflectionTestUtils.setField(outboxRelay, "confirmTimeoutMs", 100L);
        ReflectionTestUtils.setField(outboxRelay, "initialBackoffSeconds", 5L);
        ReflectionTestUtils.setField(outboxRelay, "maxBackoffSeconds", 300L);
    }

    @Test
    void relayMarksEventSentWhenConfirmAckAndMessageIsNotReturned() throws Exception {
        OutboxEvent event = outboxEvent(1, 0, objectMapper.writeValueAsString(priceAlertMessage()));
        CorrelationData correlationData = ackCorrelationData(event.getEventKey());
        when(outboxEventMapper.selectReadyEvents(any(), any(), eq(20))).thenReturn(List.of(event));
        when(priceAlertProducer.send(any(PriceAlertMessage.class))).thenReturn(correlationData);
        when(priceAlertProducer.isReturned(event.getEventKey())).thenReturn(false);

        outboxRelay.relayPendingEvents();

        verify(outboxEventMapper).markSent(eq(1L), any(LocalDateTime.class));
        verify(priceAlertProducer).clearReturned(event.getEventKey());
    }

    @Test
    void relayMarksReturnedEventDeadEvenWhenConfirmAckArrives() throws Exception {
        OutboxEvent event = outboxEvent(2, 0, objectMapper.writeValueAsString(priceAlertMessage()));
        when(outboxEventMapper.selectReadyEvents(any(), any(), eq(20))).thenReturn(List.of(event));
        when(priceAlertProducer.send(any(PriceAlertMessage.class))).thenReturn(ackCorrelationData(event.getEventKey()));
        when(priceAlertProducer.isReturned(event.getEventKey())).thenReturn(true);

        outboxRelay.relayPendingEvents();

        verify(outboxEventMapper).markDead(eq(2L), eq(1), any(String.class), any(LocalDateTime.class));
        verify(outboxEventMapper, never()).markSent(any(), any());
    }

    @Test
    void relayMarksInvalidPayloadDeadWithoutPublishing() {
        OutboxEvent event = outboxEvent(3, 0, "{not-json");
        when(outboxEventMapper.selectReadyEvents(any(), any(), eq(20))).thenReturn(List.of(event));

        outboxRelay.relayPendingEvents();

        verify(priceAlertProducer, never()).send(any());
        verify(outboxEventMapper).markDead(eq(3L), eq(1), any(String.class), any(LocalDateTime.class));
    }

    @Test
    void relayMarksNackRetryableWithIncrementedAttemptsAndBackoff() throws Exception {
        OutboxEvent event = outboxEvent(4, 1, objectMapper.writeValueAsString(priceAlertMessage()));
        when(outboxEventMapper.selectReadyEvents(any(), any(), eq(20))).thenReturn(List.of(event));
        when(priceAlertProducer.send(any(PriceAlertMessage.class))).thenReturn(nackCorrelationData(event.getEventKey()));

        outboxRelay.relayPendingEvents();

        ArgumentCaptor<LocalDateTime> retryAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxEventMapper).markRetryable(eq(4L), eq(2), retryAtCaptor.capture(), any(String.class), any(LocalDateTime.class));
        assertThat(retryAtCaptor.getValue()).isAfter(LocalDateTime.now());
    }

    @Test
    void relayMarksEventDeadWhenAttemptsReachMaxAttempts() throws Exception {
        OutboxEvent event = outboxEvent(5, 4, objectMapper.writeValueAsString(priceAlertMessage()));
        when(outboxEventMapper.selectReadyEvents(any(), any(), eq(20))).thenReturn(List.of(event));
        when(priceAlertProducer.send(any(PriceAlertMessage.class))).thenReturn(nackCorrelationData(event.getEventKey()));

        outboxRelay.relayPendingEvents();

        verify(outboxEventMapper).markDead(eq(5L), eq(5), any(String.class), any(LocalDateTime.class));
        verify(outboxEventMapper, never()).markRetryable(any(), any(), any(), any(), any());
    }

    private CorrelationData ackCorrelationData(String eventKey) {
        CorrelationData correlationData = new CorrelationData(eventKey);
        correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
        return correlationData;
    }

    private CorrelationData nackCorrelationData(String eventKey) {
        CorrelationData correlationData = new CorrelationData(eventKey);
        correlationData.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
        return correlationData;
    }

    private OutboxEvent outboxEvent(long id, int attempts, String payload) {
        return OutboxEvent.builder()
                .id(id)
                .eventKey("TARGET_PRICE_REACHED:99:1:5:80.00:79.00:1782468930000")
                .eventType("PRICE_ALERT_TARGET_REACHED_V1")
                .payload(payload)
                .status(OutboxEventStatus.PENDING)
                .attempts(attempts)
                .nextRetryAt(LocalDateTime.now().minusSeconds(1))
                .build();
    }

    private PriceAlertMessage priceAlertMessage() {
        String eventKey = "TARGET_PRICE_REACHED:99:1:5:80.00:79.00:1782468930000";
        return PriceAlertMessage.builder()
                .messageId(eventKey)
                .eventKey(eventKey)
                .userId(99L)
                .productId(1L)
                .watchlistId(5L)
                .currentPrice(new BigDecimal("79.00"))
                .targetPrice(new BigDecimal("80.00"))
                .productName("Laptop")
                .triggeredAt(LocalDateTime.now())
                .build();
    }
}
