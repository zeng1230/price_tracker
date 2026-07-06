package com.example.price_tracker.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.price_tracker.entity.OutboxEvent;
import com.example.price_tracker.entity.OutboxEventStatus;
import com.example.price_tracker.common.ResultCode;
import com.example.price_tracker.entity.PriceHistory;
import com.example.price_tracker.entity.Product;
import com.example.price_tracker.entity.Watchlist;
import com.example.price_tracker.mapper.OutboxEventMapper;
import com.example.price_tracker.exception.BusinessException;
import com.example.price_tracker.mapper.PriceHistoryMapper;
import com.example.price_tracker.mapper.ProductMapper;
import com.example.price_tracker.mapper.WatchlistMapper;
import com.example.price_tracker.mq.message.PriceAlertEventKeyBuilder;
import com.example.price_tracker.mq.message.PriceAlertMessage;
import com.example.price_tracker.provider.PriceProvider;
import com.example.price_tracker.provider.PriceProviderException;
import com.example.price_tracker.provider.PriceProviderRouter;
import com.example.price_tracker.provider.PriceQuote;
import com.example.price_tracker.redis.RedisCacheService;
import com.example.price_tracker.metrics.PriceTrackerMetrics;
import com.example.price_tracker.redis.RedisKeyManager;
import com.example.price_tracker.service.PriceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;


import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class PriceServiceImpl implements PriceService {

    private static final int ACTIVE_STATUS = 1;
    private static final int NOTIFY_ENABLED = 1;
    private static final String PRICE_ALERT_EVENT_TYPE = "PRICE_ALERT_TARGET_REACHED_V1";
    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("100.00");
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int MAX_REFRESH_RETRIES = 2;

    private final ProductMapper productMapper;
    private final PriceHistoryMapper priceHistoryMapper;
    private final WatchlistMapper watchlistMapper;
    private final OutboxEventMapper outboxEventMapper;
    private final PriceProviderRouter priceProviderRouter;
    private final RedisCacheService cacheService;
    private final PriceTrackerMetrics metrics;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ThreadLocal<String> lastResolvedProvider = new ThreadLocal<>();

    public PriceServiceImpl(ProductMapper productMapper,
                            PriceHistoryMapper priceHistoryMapper,
                            WatchlistMapper watchlistMapper,
                            OutboxEventMapper outboxEventMapper,
                            PriceProviderRouter priceProviderRouter,
                            RedisCacheService cacheService,
                            PriceTrackerMetrics metrics,
                            ObjectMapper objectMapper,
                            PlatformTransactionManager transactionManager) {
        this.productMapper = productMapper;
        this.priceHistoryMapper = priceHistoryMapper;
        this.watchlistMapper = watchlistMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.priceProviderRouter = priceProviderRouter;
        this.cacheService = cacheService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Value("${notification.idempotent.ttl-minutes:10}")
    private long notificationIdempotentTtlMinutes = 10;

    @Value("${price-tracker.price-refresh.batch-size:100}")
    private int priceRefreshBatchSize = DEFAULT_BATCH_SIZE;

    @Override
    @Transactional
    public void refreshProductPrice(Long productId) {
        lastResolvedProvider.remove();
        try {
            refreshProductPriceInternal(productId);
            String providerCode = lastResolvedProvider.get();
            metrics.recordPriceRefreshFinal(PriceTrackerMetrics.RESULT_SUCCESS, providerCode != null ? providerCode : "unknown");
        } catch (RuntimeException exception) {
            String providerCode = lastResolvedProvider.get();
            metrics.recordPriceRefreshFinal(PriceTrackerMetrics.RESULT_FAILED, providerCode != null ? providerCode : "unknown");
            throw exception;
        } finally {
            lastResolvedProvider.remove();
        }
    }

    private int refreshProductPriceInternal(Long productId) {
        Product product = getActiveProductOrThrow(productId);
        BigDecimal oldPrice = product.getCurrentPrice() == null ? DEFAULT_PRICE : product.getCurrentPrice();
        PriceProvider priceProvider = null;
        long startNanos = System.nanoTime();
        try {
            priceProvider = priceProviderRouter.route(product);
            lastResolvedProvider.set(priceProvider.providerCode());
            PriceQuote quote = priceProvider.fetchPrice(product);
            long durationNanos = System.nanoTime() - startNanos;
            metrics.recordPriceProviderFetch(priceProvider.providerCode(), PriceTrackerMetrics.RESULT_SUCCESS, Duration.ofNanos(durationNanos));

            BigDecimal newPrice = quote.price();
            LocalDateTime capturedAt = quote.capturedAt();
            product.setCurrency(quote.currency());
            product.setLastCheckedAt(capturedAt);
            if (newPrice.compareTo(oldPrice) == 0) {
                productMapper.updateById(product);
                clearProductCache(productId);
                metrics.recordPriceRefreshAttempt(PriceTrackerMetrics.RESULT_SUCCESS, priceProvider.providerCode());
                return 0;
            }
            product.setCurrentPrice(newPrice);
            product.setUpdatedAt(capturedAt);
            productMapper.updateById(product);
            clearProductCache(productId);
            priceHistoryMapper.insert(PriceHistory.builder()
                    .productId(product.getId())
                    .oldPrice(oldPrice)
                    .newPrice(newPrice)
                    .capturedAt(capturedAt)
                    .source(quote.source())
                    .build());
            int notificationTriggeredCount = 0;
            List<Watchlist> watchlists = watchlistMapper.selectList(new LambdaQueryWrapper<Watchlist>()
                    .eq(Watchlist::getProductId, productId)
                    .eq(Watchlist::getStatus, ACTIVE_STATUS)
                    .eq(Watchlist::getNotifyEnabled, NOTIFY_ENABLED));
            for (Watchlist watchlist : watchlists) {
                if (shouldNotify(watchlist, newPrice)) {
                    if (sendAlertIfNotDuplicate(product, watchlist, newPrice, capturedAt)) {
                        notificationTriggeredCount++;
                    }
                }
            }
            metrics.recordPriceRefreshAttempt(PriceTrackerMetrics.RESULT_SUCCESS, priceProvider.providerCode());
            return notificationTriggeredCount;
        } catch (PriceProviderException exception) {
            long durationNanos = System.nanoTime() - startNanos;
            String providerCode = (priceProvider != null) ? priceProvider.providerCode() : "unknown";
            if (priceProvider != null) {
                lastResolvedProvider.set(providerCode);
                metrics.recordPriceProviderFetch(providerCode, PriceTrackerMetrics.RESULT_FAILED, Duration.ofNanos(durationNanos));
                metrics.recordPriceProviderFailure(providerCode, exception.getFailureType().name());
            }
            metrics.recordPriceRefreshAttempt(PriceTrackerMetrics.RESULT_FAILED, providerCode);
            log.warn("price provider fetch failed, productId={}, providerCode={}, failureType={}, retryable={}, error={}",
                    productId, providerCode, exception.getFailureType(), exception.isRetryable(), exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            long durationNanos = System.nanoTime() - startNanos;
            String providerCode = (priceProvider != null) ? priceProvider.providerCode() : "unknown";
            if (priceProvider != null) {
                lastResolvedProvider.set(providerCode);
                metrics.recordPriceProviderFetch(providerCode, PriceTrackerMetrics.RESULT_FAILED, Duration.ofNanos(durationNanos));
            }
            metrics.recordPriceRefreshAttempt(PriceTrackerMetrics.RESULT_FAILED, providerCode);
            throw exception;
        }
    }

    @Override
    public void refreshActiveProducts() {
        long startAt = System.currentTimeMillis();
        int batchSize = resolveBatchSize();
        long pageNum = 1L;
        long scannedCount = 0L;
        long successCount = 0L;
        long failedCount = 0L;
        long notificationTriggeredCount = 0L;
        log.info("price refresh task start, batchSize={}", batchSize);

        while (true) {
            Page<Product> pageRequest = new Page<>(pageNum, batchSize);
            Page<Product> page = productMapper.selectPage(pageRequest, new LambdaQueryWrapper<Product>()
                    .eq(Product::getStatus, ACTIVE_STATUS)
                    .orderByAsc(Product::getId));
            List<Product> products = page.getRecords();
            if (products == null || products.isEmpty()) {
                break;
            }

            log.info("price refresh batch start, pageNum={}, batchSize={}, batchCount={}",
                    pageNum, batchSize, products.size());
            for (Product product : products) {
                scannedCount++;
                try {
                    notificationTriggeredCount += refreshProductWithRetry(product.getId());
                    successCount++;
                } catch (Exception exception) {
                    failedCount++;
                    log.warn("price refresh product failed, productId={}, retries={}, message={}",
                            product.getId(), MAX_REFRESH_RETRIES, exception.getMessage());
                }
            }

            if (pageNum >= page.getPages()) {
                break;
            }
            pageNum++;
        }

        log.info("price refresh task finished, scanned count={}, success count={}, failed count={}, notification triggered count={}, total cost={}ms",
                scannedCount, successCount, failedCount, notificationTriggeredCount, System.currentTimeMillis() - startAt);
    }

    private int refreshProductWithRetry(Long productId) {
        RuntimeException lastException = null;
        lastResolvedProvider.remove();
        for (int attempt = 0; attempt <= MAX_REFRESH_RETRIES; attempt++) {
            try {
                Integer notificationCount = transactionTemplate.execute(status -> refreshProductPriceInternal(productId));
                String providerCode = lastResolvedProvider.get();
                metrics.recordPriceRefreshFinal(PriceTrackerMetrics.RESULT_SUCCESS, providerCode != null ? providerCode : "unknown");
                lastResolvedProvider.remove();
                return notificationCount != null ? notificationCount : 0;
            } catch (PriceProviderException exception) {
                lastException = exception;
                String providerCode = lastResolvedProvider.get();
                log.warn("price refresh attempt failed due to provider error, productId={}, attempt={}, maxRetries={}, failureType={}, retryable={}, message={}",
                        productId, attempt + 1, MAX_REFRESH_RETRIES, exception.getFailureType(), exception.isRetryable(), exception.getMessage());
                if (!exception.isRetryable()) {
                    metrics.recordPriceRefreshFinal(PriceTrackerMetrics.RESULT_FAILED, providerCode != null ? providerCode : "unknown");
                    lastResolvedProvider.remove();
                    throw exception;
                }
            } catch (RuntimeException exception) {
                lastException = exception;
                log.warn("price refresh attempt failed, productId={}, attempt={}, maxRetries={}, message={}",
                        productId, attempt + 1, MAX_REFRESH_RETRIES, exception.getMessage());
            }
        }
        String providerCode = lastResolvedProvider.get();
        metrics.recordPriceRefreshFinal(PriceTrackerMetrics.RESULT_FAILED, providerCode != null ? providerCode : "unknown");
        lastResolvedProvider.remove();
        throw lastException;
    }

    private int resolveBatchSize() {
        return priceRefreshBatchSize > 0 ? priceRefreshBatchSize : DEFAULT_BATCH_SIZE;
    }

    private boolean shouldNotify(Watchlist watchlist, BigDecimal newPrice) {
        return watchlist.getTargetPrice() != null && newPrice.compareTo(watchlist.getTargetPrice()) <= 0;
    }

    private PriceAlertMessage buildPriceAlertMessage(Product product, Watchlist watchlist, BigDecimal newPrice, LocalDateTime now) {
        String eventKey = PriceAlertEventKeyBuilder.buildTargetPriceReachedKey(
                watchlist.getUserId(),
                product.getId(),
                watchlist.getId(),
                watchlist.getTargetPrice(),
                newPrice,
                now);
        return PriceAlertMessage.builder()
                .messageId(eventKey)
                .eventKey(eventKey)
                .userId(watchlist.getUserId())
                .productId(product.getId())
                .watchlistId(watchlist.getId())
                .currentPrice(newPrice)
                .targetPrice(watchlist.getTargetPrice())
                .productName(product.getProductName())
                .triggeredAt(now)
                .build();
    }

    private boolean sendAlertIfNotDuplicate(Product product, Watchlist watchlist, BigDecimal newPrice, LocalDateTime now) {
        String idempotentKey = RedisKeyManager.notificationIdempotentKey(
                watchlist.getUserId() + ":" + product.getId() + ":" + watchlist.getTargetPrice());
        boolean acquired = cacheService.setIfAbsent(
                idempotentKey,
                "1",
                Duration.ofMinutes(notificationIdempotentTtlMinutes));
        if (!acquired) {
            log.info("notification idempotent hit, key={}", idempotentKey);
            return false;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        log.info("Transaction rolled back, deleting redis idempotent key={}", idempotentKey);
                        cacheService.delete(idempotentKey);
                    }
                }
            });
        } else {
            log.debug("No transaction active when acquiring idempotent key={}", idempotentKey);
        }

        PriceAlertMessage message = buildPriceAlertMessage(product, watchlist, newPrice, now);
        String payload = serializeMessage(message);
        OutboxEvent event = OutboxEvent.builder()
                .eventKey(message.getEventKey())
                .eventType(PRICE_ALERT_EVENT_TYPE)
                .payload(payload)
                .status(OutboxEventStatus.PENDING)
                .attempts(0)
                .nextRetryAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            int inserted = outboxEventMapper.insertIgnore(event);
            if (inserted == 0) {
                log.info("outbox event already exists, eventKey={}, decision=idempotent_skip", message.getEventKey());
                return false;
            }
            log.info("created outbox event for price alert, eventKey={}, productId={}, userId={}, watchlistId={}",
                    message.getEventKey(), message.getProductId(), message.getUserId(), message.getWatchlistId());
            return true;
        } catch (DuplicateKeyException exception) {
            log.info("outbox event unique conflict, eventKey={}, decision=idempotent_skip", message.getEventKey());
            return false;
        }
    }

    private String serializeMessage(PriceAlertMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "failed to serialize price alert outbox payload");
        }
    }

    private Product getActiveProductOrThrow(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null || product.getStatus() == null || product.getStatus() != ACTIVE_STATUS) {
            throw new BusinessException(ResultCode.NOT_FOUND, "product not found");
        }
        return product;
    }

    private void clearProductCache(Long productId) {
        cacheService.delete(RedisKeyManager.productDetailKey(productId));
        cacheService.delete(RedisKeyManager.productPriceKey(productId));
        cacheService.delete(RedisKeyManager.nullValueKey("product:detail:" + productId));
        cacheService.delete(RedisKeyManager.nullValueKey("product:price:" + productId));
    }
}
