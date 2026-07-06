package com.example.price_tracker.provider;

import lombok.Getter;

@Getter
public class PriceProviderException extends RuntimeException {

    private final PriceProviderFailureType failureType;
    private final boolean retryable;

    public PriceProviderException(PriceProviderFailureType failureType, boolean retryable, String message) {
        super(message);
        this.failureType = failureType;
        this.retryable = retryable;
    }

    public PriceProviderException(PriceProviderFailureType failureType, boolean retryable, String message, Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
        this.retryable = retryable;
    }
}
