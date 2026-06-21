package com.example.price_tracker.provider.impl;

import com.example.price_tracker.entity.Product;
import com.example.price_tracker.provider.PriceProvider;
import com.example.price_tracker.provider.PriceQuote;
import com.example.price_tracker.util.PriceMockUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class MockPriceProvider implements PriceProvider {

    private static final String PROVIDER_CODE = "MOCK";
    private static final String DEFAULT_CURRENCY = "USD";

    private final PriceMockUtil priceMockUtil;

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public boolean supports(Product product) {
        return product != null;
    }

    @Override
    public PriceQuote fetchPrice(Product product) {
        BigDecimal price = priceMockUtil.generateNextPrice(product.getCurrentPrice());
        String currency = product.getCurrency() == null || product.getCurrency().isBlank()
                ? DEFAULT_CURRENCY
                : product.getCurrency();
        return new PriceQuote(
                price,
                currency,
                PROVIDER_CODE,
                LocalDateTime.now(),
                null,
                product.getProductName());
    }
}
