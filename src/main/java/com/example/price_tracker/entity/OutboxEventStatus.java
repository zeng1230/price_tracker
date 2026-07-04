package com.example.price_tracker.entity;

public enum OutboxEventStatus {
    PENDING,
    SENT,
    FAILED_RETRYABLE,
    DEAD
}
