package com.example.price_tracker.mq.message;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceAlertEventKeyBuilderTest {

    @Test
    void normalizesMoneyScaleWhenBuildingEventKey() {
        LocalDateTime capturedAt = LocalDateTime.of(2026, 6, 26, 10, 15, 30, 123_000_000);

        String keyWithOneDecimal = PriceAlertEventKeyBuilder.buildTargetPriceReachedKey(
                99L, 1L, 5L, new BigDecimal("10.0"), new BigDecimal("8.1"), capturedAt);
        String keyWithTwoDecimals = PriceAlertEventKeyBuilder.buildTargetPriceReachedKey(
                99L, 1L, 5L, new BigDecimal("10.00"), new BigDecimal("8.10"), capturedAt);

        assertEquals(keyWithTwoDecimals, keyWithOneDecimal);
        assertEquals("TARGET_PRICE_REACHED:99:1:5:10.00:8.10:1782468930123", keyWithOneDecimal);
    }

    @Test
    void usesEpochMillisForStableTimeFormat() {
        LocalDateTime capturedAt = LocalDateTime.of(2026, 6, 26, 10, 15, 30, 987_654_321);

        String eventKey = PriceAlertEventKeyBuilder.buildTargetPriceReachedKey(
                99L, 1L, 5L, new BigDecimal("10.00"), new BigDecimal("8.10"), capturedAt);

        assertEquals("TARGET_PRICE_REACHED:99:1:5:10.00:8.10:1782468930987", eventKey);
    }
}
