package com.example.price_tracker.provider;

import com.example.price_tracker.entity.Product;

public interface PriceProvider {

    String providerCode();

    boolean supports(Product product);

    PriceQuote fetchPrice(Product product);
}
