package com.example.price_tracker.provider;

public enum PriceProviderFailureType {
    TIMEOUT,
    RATE_LIMITED,
    AUTHENTICATION_FAILED,
    PRODUCT_NOT_FOUND,
    INVALID_RESPONSE,
    UPSTREAM_ERROR
}
