package com.example.price_tracker.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.price_tracker.entity.OutboxEvent;
import com.example.price_tracker.entity.OutboxEventStatus;
import com.example.price_tracker.entity.PriceHistory;
import com.example.price_tracker.entity.Product;
import com.example.price_tracker.entity.Watchlist;
import com.example.price_tracker.mapper.OutboxEventMapper;
import com.example.price_tracker.mapper.PriceHistoryMapper;
import com.example.price_tracker.mapper.ProductMapper;
import com.example.price_tracker.mapper.WatchlistMapper;
import com.example.price_tracker.mq.message.PriceAlertMessage;
import com.example.price_tracker.mq.producer.PriceAlertProducer;
import com.example.price_tracker.provider.PriceProvider;
import com.example.price_tracker.provider.PriceProviderException;
import com.example.price_tracker.provider.PriceProviderFailureType;
import com.example.price_tracker.provider.PriceProviderRouter;
import com.example.price_tracker.provider.PriceQuote;
import com.example.price_tracker.metrics.PriceTrackerMetrics;
import com.example.price_tracker.redis.RedisCacheService;
import com.example.price_tracker.redis.RedisKeyManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PriceServiceImplTest {

    private static final LocalDateTime CAPTURED_AT = LocalDateTime.of(2026, 6, 21, 11, 0);

    @Mock
    private ProductMapper productMapper;

    @Mock
    private PriceHistoryMapper priceHistoryMapper;

    @Mock
    private WatchlistMapper watchlistMapper;

    @Mock
    private OutboxEventMapper outboxEventMapper;

    @Mock
    private PriceAlertProducer priceAlertProducer;

    @Mock
    private PriceProviderRouter priceProviderRouter;

    @Mock
    private PriceProvider priceProvider;

    @Mock
    private RedisCacheService cacheService;

    @Mock
    private PriceTrackerMetrics metrics;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private PlatformTransactionManager transactionManager;

    private PriceServiceImpl priceService;

    @BeforeEach
    void setUp() {
        TransactionStatus mockStatus = mock(TransactionStatus.class);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(mockStatus);

        priceService = new PriceServiceImpl(
                productMapper,
                priceHistoryMapper,
                watchlistMapper,
                outboxEventMapper,
                priceProviderRouter,
                cacheService,
                metrics,
                objectMapper,
                transactionManager
        );
    }

    @Test
    void refreshProductPriceCreatesHistoryAndAlertMessageWhenTargetReached() {
        when(productMapper.selectById(1L)).thenReturn(activeProduct());
        mockQuote("79.00", "CNY");
        when(watchlistMapper.selectList(any())).thenReturn(List.of(activeWatchlistWithoutDedupPrice()));
        when(cacheService.setIfAbsent(
                RedisKeyManager.notificationIdempotentKey("99:1:80.00"),
                "1",
                java.time.Duration.ofMinutes(10))).thenReturn(true);
        when(outboxEventMapper.insertIgnore(any(OutboxEvent.class))).thenReturn(1);

        priceService.refreshProductPrice(1L);

        verify(productMapper).updateById(argThat(updatedProduct()));
        verify(priceHistoryMapper).insert(argThat(createdPriceHistory("79.00")));
        verify(priceAlertProducer, never()).send(any(PriceAlertMessage.class));
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventMapper).insertIgnore(outboxCaptor.capture());
        assertThat(createdOutboxEvent().matches(outboxCaptor.getValue())).isTrue();
        verify(cacheService).delete(RedisKeyManager.productDetailKey(1L));
        verify(cacheService).delete(RedisKeyManager.productPriceKey(1L));
    }

    @Test
    void refreshProductPriceRecordsHistoryButDoesNotSendAlertWhenChangedPriceIsAboveTarget() {
        when(productMapper.selectById(1L)).thenReturn(activeProduct());
        mockQuote("81.00", "USD");
        when(watchlistMapper.selectList(any())).thenReturn(List.of(activeWatchlistWithoutDedupPrice()));

        priceService.refreshProductPrice(1L);

        verify(productMapper).updateById(argThat((Product product) ->
                new BigDecimal("81.00").compareTo(product.getCurrentPrice()) == 0));
        verify(priceHistoryMapper).insert(any(PriceHistory.class));
        verify(priceAlertProducer, never()).send(any(PriceAlertMessage.class));
        verify(cacheService).delete(RedisKeyManager.productDetailKey(1L));
        verify(cacheService).delete(RedisKeyManager.productPriceKey(1L));
    }

    @Test
    void refreshProductPriceSkipsDuplicateAlertWhenIdempotentKeyAlreadyExists() {
        when(productMapper.selectById(1L)).thenReturn(activeProduct());
        mockQuote("79.00", "USD");
        when(watchlistMapper.selectList(any())).thenReturn(List.of(activeWatchlistWithoutDedupPrice()));
        when(cacheService.setIfAbsent(
                RedisKeyManager.notificationIdempotentKey("99:1:80.00"),
                "1",
                java.time.Duration.ofMinutes(10))).thenReturn(false);

        priceService.refreshProductPrice(1L);

        verify(priceAlertProducer, never()).send(any(PriceAlertMessage.class));
    }

    @Test
    void refreshProductPriceSkipsDuplicateOutboxEventKeyWithoutFailingRefresh() {
        when(productMapper.selectById(1L)).thenReturn(activeProduct());
        mockQuote("79.00", "CNY");
        when(watchlistMapper.selectList(any())).thenReturn(List.of(activeWatchlistWithoutDedupPrice()));
        when(cacheService.setIfAbsent(
                RedisKeyManager.notificationIdempotentKey("99:1:80.00"),
                "1",
                java.time.Duration.ofMinutes(10))).thenReturn(true);
        when(outboxEventMapper.insertIgnore(any(OutboxEvent.class))).thenReturn(0);

        priceService.refreshProductPrice(1L);

        verify(priceHistoryMapper).insert(any(PriceHistory.class));
        verify(priceAlertProducer, never()).send(any(PriceAlertMessage.class));
        verify(outboxEventMapper).insertIgnore(any(OutboxEvent.class));
    }

    @Test
    void refreshActiveProductsProcessesAllActiveProductsByPage() {
        ReflectionTestUtils.setField(priceService, "priceRefreshBatchSize", 2);
        when(productMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<Product> request = invocation.getArgument(0);
            Page<Product> page = new Page<>(request.getCurrent(), request.getSize());
            page.setTotal(3);
            if (request.getCurrent() == 1L) {
                page.setRecords(List.of(activeProduct(1L), activeProduct(2L)));
            } else if (request.getCurrent() == 2L) {
                page.setRecords(List.of(activeProduct(3L)));
            } else {
                page.setRecords(List.of());
            }
            return page;
        });
        when(productMapper.selectById(1L)).thenReturn(activeProduct(1L));
        when(productMapper.selectById(2L)).thenReturn(activeProduct(2L));
        when(productMapper.selectById(3L)).thenReturn(activeProduct(3L));
        mockQuote("101.00", "USD");
        when(watchlistMapper.selectList(any())).thenReturn(List.of());

        priceService.refreshActiveProducts();

        verify(productMapper, times(3)).updateById(any(Product.class));
        verify(productMapper, times(2)).selectPage(any(Page.class), any());
    }

    @Test
    void refreshActiveProductsContinuesWhenSingleProductFails() {
        ReflectionTestUtils.setField(priceService, "priceRefreshBatchSize", 2);
        when(productMapper.selectPage(any(Page.class), any())).thenReturn(activeProductPage(activeProduct(1L), activeProduct(2L)));
        when(productMapper.selectById(1L)).thenThrow(new RuntimeException("refresh failed"));
        when(productMapper.selectById(2L)).thenReturn(activeProduct(2L));
        mockQuote("101.00", "USD");
        when(watchlistMapper.selectList(any())).thenReturn(List.of());

        priceService.refreshActiveProducts();

        verify(productMapper).updateById(argThat((Product product) -> product.getId().equals(2L)));
    }

    @Test
    void refreshActiveProductsRetriesFailedProductAtMostTwoTimes() {
        ReflectionTestUtils.setField(priceService, "priceRefreshBatchSize", 1);
        when(productMapper.selectPage(any(Page.class), any())).thenReturn(activeProductPage(activeProduct(1L)));
        when(productMapper.selectById(1L)).thenThrow(new RuntimeException("refresh failed"));

        priceService.refreshActiveProducts();

        verify(productMapper, times(3)).selectById(1L);
        verify(productMapper, never()).updateById(any(Product.class));
        verify(productMapper, atLeastOnce()).selectPage(any(Page.class), any());
    }

    private ArgumentMatcher<Product> updatedProduct() {
        return product -> new BigDecimal("79.00").compareTo(product.getCurrentPrice()) == 0
                && "CNY".equals(product.getCurrency())
                && CAPTURED_AT.equals(product.getLastCheckedAt())
                && CAPTURED_AT.equals(product.getUpdatedAt());
    }

    private ArgumentMatcher<PriceHistory> createdPriceHistory(String expectedPrice) {
        return history -> history.getProductId().equals(1L)
                && new BigDecimal("100.00").compareTo(history.getOldPrice()) == 0
                && new BigDecimal(expectedPrice).compareTo(history.getNewPrice()) == 0
                && "MOCK".equals(history.getSource())
                && CAPTURED_AT.equals(history.getCapturedAt());
    }

    private ArgumentMatcher<PriceAlertMessage> createdPriceAlertMessage() {
        return message -> message.getMessageId() != null
                && !message.getMessageId().isBlank()
                && "TARGET_PRICE_REACHED:99:1:5:80.00:79.00:1782039600000".equals(message.getEventKey())
                && message.getUserId().equals(99L)
                && message.getProductId().equals(1L)
                && message.getWatchlistId().equals(5L)
                && "Laptop".equals(message.getProductName())
                && new BigDecimal("79.00").compareTo(message.getCurrentPrice()) == 0
                && new BigDecimal("80.00").compareTo(message.getTargetPrice()) == 0
                && CAPTURED_AT.equals(message.getTriggeredAt());
    }

    private ArgumentMatcher<OutboxEvent> createdOutboxEvent() {
        return event -> "TARGET_PRICE_REACHED:99:1:5:80.00:79.00:1782039600000".equals(event.getEventKey())
                && "PRICE_ALERT_TARGET_REACHED_V1".equals(event.getEventType())
                && OutboxEventStatus.PENDING == event.getStatus()
                && event.getAttempts() == 0
                && CAPTURED_AT.equals(event.getNextRetryAt())
                && event.getPayload() != null
                && event.getPayload().contains("\"eventKey\":\"TARGET_PRICE_REACHED:99:1:5:80.00:79.00:1782039600000\"")
                && event.getPayload().contains("\"messageId\":\"TARGET_PRICE_REACHED:99:1:5:80.00:79.00:1782039600000\"");
    }

    private Product activeProduct() {
        return activeProduct(1L);
    }

    private Product activeProduct(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setProductName("Laptop");
        product.setCurrentPrice(new BigDecimal("100.00"));
        product.setCurrency("USD");
        product.setStatus(1);
        return product;
    }

    @SafeVarargs
    private Page<Product> activeProductPage(Product... products) {
        Page<Product> page = new Page<>(1, products.length == 0 ? 1 : products.length);
        page.setTotal(products.length);
        page.setRecords(List.of(products));
        return page;
    }

    private Watchlist activeWatchlistWithoutDedupPrice() {
        Watchlist watchlist = new Watchlist();
        watchlist.setId(5L);
        watchlist.setUserId(99L);
        watchlist.setProductId(1L);
        watchlist.setTargetPrice(new BigDecimal("80.00"));
        watchlist.setNotifyEnabled(1);
        watchlist.setStatus(1);
        return watchlist;
    }

    private void mockQuote(String price, String currency) {
        when(priceProviderRouter.route(any(Product.class))).thenReturn(priceProvider);
        when(priceProvider.fetchPrice(any(Product.class))).thenReturn(new PriceQuote(
                new BigDecimal(price),
                currency,
                "MOCK",
                CAPTURED_AT,
                null,
                "Laptop"));
    }

    @Test
    void refreshProductPriceThrowsPriceProviderExceptionAndRecordsSpecificMetrics() {
        when(productMapper.selectById(1L)).thenReturn(activeProduct());
        when(priceProviderRouter.route(any(Product.class))).thenReturn(priceProvider);
        when(priceProvider.providerCode()).thenReturn("SERPAPI");
        when(priceProvider.fetchPrice(any(Product.class))).thenThrow(
                new PriceProviderException(PriceProviderFailureType.RATE_LIMITED, true, "serpapi rate limited"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> priceService.refreshProductPrice(1L))
                .isInstanceOf(PriceProviderException.class);

        verify(metrics).recordPriceProviderFailure("SERPAPI", "RATE_LIMITED");
        verify(metrics).recordPriceProviderFetch(eq("SERPAPI"), eq(PriceTrackerMetrics.RESULT_FAILED), any(java.time.Duration.class));
        verify(metrics).recordPriceRefreshAttempt(PriceTrackerMetrics.RESULT_FAILED, "SERPAPI");
    }

    @Test
    void refreshActiveProductsAbortsImmediatelyOnNonRetryableProviderException() {
        ReflectionTestUtils.setField(priceService, "priceRefreshBatchSize", 1);
        when(productMapper.selectPage(any(Page.class), any())).thenReturn(activeProductPage(activeProduct(1L)));
        when(productMapper.selectById(1L)).thenReturn(activeProduct(1L));
        when(priceProviderRouter.route(any(Product.class))).thenReturn(priceProvider);
        when(priceProvider.providerCode()).thenReturn("SERPAPI");
        when(priceProvider.fetchPrice(any(Product.class))).thenThrow(
                new PriceProviderException(PriceProviderFailureType.AUTHENTICATION_FAILED, false, "invalid key"));

        priceService.refreshActiveProducts();

        // Should only query the product once and abort retrying because it's not retryable
        verify(productMapper, times(1)).selectById(1L);
    }
}
