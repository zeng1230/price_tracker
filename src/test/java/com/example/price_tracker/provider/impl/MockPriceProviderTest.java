package com.example.price_tracker.provider.impl;

import com.example.price_tracker.entity.Product;
import com.example.price_tracker.provider.PriceQuote;
import com.example.price_tracker.util.PriceMockUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MockPriceProviderTest {

    private PriceMockUtil priceMockUtil;
    private MockPriceProvider provider;

    @BeforeEach
    void setUp() {
        priceMockUtil = mock(PriceMockUtil.class);
        provider = new MockPriceProvider(priceMockUtil);
    }

    @Test
    void returnsValidQuoteUsingProductCurrency() {
        Product product = product("CNY");
        when(priceMockUtil.generateNextPrice(product.getCurrentPrice()))
                .thenReturn(new BigDecimal("91.50"));

        PriceQuote quote = provider.fetchPrice(product);

        assertEquals("MOCK", provider.providerCode());
        assertEquals(new BigDecimal("91.50"), quote.price());
        assertEquals("CNY", quote.currency());
        assertEquals("MOCK", quote.source());
        assertNotNull(quote.capturedAt());
        assertEquals("Laptop", quote.productTitle());
        verify(priceMockUtil).generateNextPrice(new BigDecimal("100.00"));
    }

    @Test
    void defaultsBlankProductCurrencyToUsd() {
        Product product = product(" ");
        when(priceMockUtil.generateNextPrice(product.getCurrentPrice()))
                .thenReturn(new BigDecimal("100.00"));

        PriceQuote quote = provider.fetchPrice(product);

        assertEquals("USD", quote.currency());
    }

    @Test
    void supportsEveryNonNullProductAsLowestPriorityFallback() {
        Order order = MockPriceProvider.class.getAnnotation(Order.class);

        assertTrue(provider.supports(product(null)));
        assertFalse(provider.supports(null));
        assertNotNull(order);
        assertEquals(Ordered.LOWEST_PRECEDENCE, order.value());
    }

    private Product product(String currency) {
        Product product = new Product();
        product.setId(1L);
        product.setProductName("Laptop");
        product.setPlatform("legacy-value");
        product.setCurrentPrice(new BigDecimal("100.00"));
        product.setCurrency(currency);
        return product;
    }
}
