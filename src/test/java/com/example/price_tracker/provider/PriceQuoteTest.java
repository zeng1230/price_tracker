package com.example.price_tracker.provider;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PriceQuoteTest {

    private static final LocalDateTime CAPTURED_AT = LocalDateTime.of(2026, 6, 21, 10, 30);

    @Test
    void createsImmutableQuoteWithNormalizedCurrencyAndSource() {
        PriceQuote quote = new PriceQuote(
                new BigDecimal("99.00"),
                " USD ",
                " MOCK ",
                CAPTURED_AT,
                "external-1",
                "Laptop");

        assertEquals(new BigDecimal("99.00"), quote.price());
        assertEquals("USD", quote.currency());
        assertEquals("MOCK", quote.source());
        assertEquals(CAPTURED_AT, quote.capturedAt());
        assertEquals("external-1", quote.externalProductId());
        assertEquals("Laptop", quote.productTitle());
    }

    @Test
    void rejectsNullPrice() {
        assertThrows(IllegalArgumentException.class,
                () -> quote(null, "USD", "MOCK", CAPTURED_AT));
    }

    @Test
    void rejectsNegativePrice() {
        assertThrows(IllegalArgumentException.class,
                () -> quote(new BigDecimal("-0.01"), "USD", "MOCK", CAPTURED_AT));
    }

    @Test
    void rejectsBlankCurrency() {
        assertThrows(IllegalArgumentException.class,
                () -> quote(BigDecimal.ZERO, " ", "MOCK", CAPTURED_AT));
    }

    @Test
    void rejectsBlankSource() {
        assertThrows(IllegalArgumentException.class,
                () -> quote(BigDecimal.ZERO, "USD", " ", CAPTURED_AT));
    }

    @Test
    void rejectsNullCapturedAt() {
        assertThrows(IllegalArgumentException.class,
                () -> quote(BigDecimal.ZERO, "USD", "MOCK", null));
    }

    private PriceQuote quote(BigDecimal price, String currency, String source, LocalDateTime capturedAt) {
        return new PriceQuote(price, currency, source, capturedAt, null, null);
    }
}
