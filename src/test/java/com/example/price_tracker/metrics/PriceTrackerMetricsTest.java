package com.example.price_tracker.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PriceTrackerMetricsTest {

    private MeterRegistry registry;
    private PriceTrackerMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new PriceTrackerMetrics(registry);
    }

    @Test
    void recordPriceRefreshAttemptIncrementsCounter() {
        metrics.recordPriceRefreshAttempt(PriceTrackerMetrics.RESULT_SUCCESS, "MOCK");

        Counter counter = registry.find(PriceTrackerMetrics.METRIC_PRICE_REFRESH)
                .tag(PriceTrackerMetrics.TAG_RESULT, PriceTrackerMetrics.RESULT_SUCCESS)
                .tag(PriceTrackerMetrics.TAG_PROVIDER, "MOCK")
                .counter();

        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void recordPriceRefreshFinalIncrementsCounter() {
        metrics.recordPriceRefreshFinal(PriceTrackerMetrics.RESULT_FAILED, "MOCK");

        Counter counter = registry.find(PriceTrackerMetrics.METRIC_PRICE_REFRESH_FINAL)
                .tag(PriceTrackerMetrics.TAG_RESULT, PriceTrackerMetrics.RESULT_FAILED)
                .tag(PriceTrackerMetrics.TAG_PROVIDER, "MOCK")
                .counter();

        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void recordPriceProviderFetchRecordsDuration() {
        metrics.recordPriceProviderFetch("MOCK", PriceTrackerMetrics.RESULT_SUCCESS, Duration.ofMillis(150));

        Timer timer = registry.find(PriceTrackerMetrics.METRIC_PRICE_PROVIDER_FETCH)
                .tag(PriceTrackerMetrics.TAG_PROVIDER, "MOCK")
                .tag(PriceTrackerMetrics.TAG_RESULT, PriceTrackerMetrics.RESULT_SUCCESS)
                .timer();

        assertNotNull(timer);
        assertEquals(1, timer.count());
        assertEquals(150.0, timer.totalTime(TimeUnit.MILLISECONDS), 0.01);
    }

    @Test
    void recordPriceAlertPublishIncrementsCounter() {
        metrics.recordPriceAlertPublish(PriceTrackerMetrics.RESULT_SUCCESS);

        Counter counter = registry.find(PriceTrackerMetrics.METRIC_PRICE_ALERT_PUBLISH)
                .tag(PriceTrackerMetrics.TAG_RESULT, PriceTrackerMetrics.RESULT_SUCCESS)
                .counter();

        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void recordPriceAlertConsumeIncrementsCounter() {
        metrics.recordPriceAlertConsume(PriceTrackerMetrics.RESULT_DUPLICATE);

        Counter counter = registry.find(PriceTrackerMetrics.METRIC_PRICE_ALERT_CONSUME)
                .tag(PriceTrackerMetrics.TAG_RESULT, PriceTrackerMetrics.RESULT_DUPLICATE)
                .counter();

        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void recordRateLimitBlockIncrementsCounter() {
        metrics.recordRateLimitBlock("watchlist_add");

        Counter counter = registry.find(PriceTrackerMetrics.METRIC_RATE_LIMIT_BLOCK)
                .tag(PriceTrackerMetrics.TAG_API, "watchlist_add")
                .counter();

        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }
}
