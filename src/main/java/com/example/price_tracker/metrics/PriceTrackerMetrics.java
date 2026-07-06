package com.example.price_tracker.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PriceTrackerMetrics {

    // Metric names
    public static final String METRIC_PRICE_REFRESH = "price_refresh_total";
    public static final String METRIC_PRICE_REFRESH_FINAL = "price_refresh_final_total";
    public static final String METRIC_PRICE_PROVIDER_FETCH = "price_provider_fetch_seconds";
    public static final String METRIC_PRICE_ALERT_PUBLISH = "price_alert_publish_total";
    public static final String METRIC_PRICE_ALERT_CONSUME = "price_alert_consume_total";
    public static final String METRIC_RATE_LIMIT_BLOCK = "rate_limit_block_total";
    public static final String METRIC_PRICE_PROVIDER_FAILURE = "price_provider_failure_total";
    public static final String METRIC_OUTBOX_RELAY = "outbox_relay_total";
    public static final String METRIC_NOTIFICATION_DELIVERY = "notification_delivery_total";

    // Tag keys
    public static final String TAG_RESULT = "result";
    public static final String TAG_PROVIDER = "provider";
    public static final String TAG_API = "api";
    public static final String TAG_FAILURE_TYPE = "failure_type";

    // Tag values for RESULT
    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_FAILED = "failed";
    public static final String RESULT_SKIPPED = "skipped";
    public static final String RESULT_DUPLICATE = "duplicate";
    public static final String RESULT_RETURNED = "returned";

    private final MeterRegistry registry;

    public PriceTrackerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordPriceRefreshAttempt(String result, String provider) {
        if (registry != null) {
            registry.counter(METRIC_PRICE_REFRESH, TAG_RESULT, result, TAG_PROVIDER, provider).increment();
        }
    }

    public void recordPriceRefreshFinal(String result, String provider) {
        if (registry != null) {
            registry.counter(METRIC_PRICE_REFRESH_FINAL, TAG_RESULT, result, TAG_PROVIDER, provider).increment();
        }
    }

    public void recordPriceProviderFetch(String provider, String result, Duration duration) {
        if (registry != null) {
            registry.timer(METRIC_PRICE_PROVIDER_FETCH, TAG_PROVIDER, provider, TAG_RESULT, result).record(duration);
        }
    }

    public void recordPriceAlertPublish(String result) {
        if (registry != null) {
            registry.counter(METRIC_PRICE_ALERT_PUBLISH, TAG_RESULT, result).increment();
        }
    }

    public void recordPriceAlertConsume(String result) {
        if (registry != null) {
            registry.counter(METRIC_PRICE_ALERT_CONSUME, TAG_RESULT, result).increment();
        }
    }

    public void recordRateLimitBlock(String api) {
        if (registry != null) {
            registry.counter(METRIC_RATE_LIMIT_BLOCK, TAG_API, api).increment();
        }
    }

    public void recordOutboxRelay(String result) {
        if (registry != null) {
            registry.counter(METRIC_OUTBOX_RELAY, TAG_RESULT, result).increment();
        }
    }

    public void recordNotificationDelivery(String result) {
        if (registry != null) {
            registry.counter(METRIC_NOTIFICATION_DELIVERY, TAG_RESULT, result).increment();
        }
    }

    public void recordPriceProviderFailure(String provider, String failureType) {
        if (registry != null) {
            registry.counter(METRIC_PRICE_PROVIDER_FAILURE, TAG_PROVIDER, provider, TAG_FAILURE_TYPE, failureType).increment();
        }
    }
}
