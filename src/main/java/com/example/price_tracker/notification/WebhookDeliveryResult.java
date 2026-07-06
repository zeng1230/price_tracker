package com.example.price_tracker.notification;

public record WebhookDeliveryResult(boolean success, boolean retryable, String error, Integer statusCode) {

    public static WebhookDeliveryResult success(int statusCode) {
        return new WebhookDeliveryResult(true, false, null, statusCode);
    }

    public static WebhookDeliveryResult retryable(String error) {
        return new WebhookDeliveryResult(false, true, error, null);
    }

    public static WebhookDeliveryResult dead(String error) {
        return new WebhookDeliveryResult(false, false, error, null);
    }
}
