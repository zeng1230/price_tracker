package com.example.price_tracker.mq.message;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class PriceAlertEventKeyBuilder {

    private static final String TARGET_PRICE_REACHED = "TARGET_PRICE_REACHED";

    private PriceAlertEventKeyBuilder() {
    }

    public static String buildTargetPriceReachedKey(Long userId,
                                                    Long productId,
                                                    Long watchlistId,
                                                    BigDecimal targetPrice,
                                                    BigDecimal currentPrice,
                                                    LocalDateTime triggeredAt) {
        return TARGET_PRICE_REACHED
                + ":"
                + userId
                + ":"
                + productId
                + ":"
                + watchlistId
                + ":"
                + normalizeMoney(targetPrice)
                + ":"
                + normalizeMoney(currentPrice)
                + ":"
                + toEpochMillis(triggeredAt);
    }

    private static String normalizeMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static long toEpochMillis(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
