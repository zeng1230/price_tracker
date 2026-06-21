package com.example.price_tracker.provider;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PriceQuote(
        BigDecimal price,
        String currency,
        String source,
        LocalDateTime capturedAt,
        String externalProductId,
        String productTitle) {

    public PriceQuote {
        if (price == null) {
            throw new IllegalArgumentException("price must not be null");
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("price must be greater than or equal to zero");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        if (capturedAt == null) {
            throw new IllegalArgumentException("capturedAt must not be null");
        }
        currency = currency.trim();
        source = source.trim();
    }
}
