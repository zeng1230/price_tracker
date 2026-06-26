package com.example.price_tracker.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.price_tracker.common.ResultCode;
import com.example.price_tracker.entity.PriceHistory;
import com.example.price_tracker.entity.Product;
import com.example.price_tracker.entity.Watchlist;
import com.example.price_tracker.exception.BusinessException;
import com.example.price_tracker.mapper.PriceHistoryMapper;
import com.example.price_tracker.mapper.ProductMapper;
import com.example.price_tracker.mapper.WatchlistMapper;
import com.example.price_tracker.mq.message.PriceAlertEventKeyBuilder;
import com.example.price_tracker.mq.message.PriceAlertMessage;
import com.example.price_tracker.mq.producer.PriceAlertProducer;
import com.example.price_tracker.provider.PriceProvider;
import com.example.price_tracker.provider.PriceProviderRouter;
import com.example.price_tracker.provider.PriceQuote;
import com.example.price_tracker.redis.RedisCacheService;
import com.example.price_tracker.redis.RedisKeyManager;
import com.example.price_tracker.service.PriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceServiceImpl implements PriceService {

    private static final int ACTIVE_STATUS = 1;
    private static final int NOTIFY_ENABLED = 1;
    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("100.00");
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int MAX_REFRESH_RETRIES = 2;

    private final ProductMapper productMapper;
    private final PriceHistoryMapper priceHistoryMapper;
    private final WatchlistMapper watchlistMapper;
    private final PriceAlertProducer priceAlertProducer;
    private final PriceProviderRouter priceProviderRouter;
    private final RedisCacheService cacheService;

    @Value("${notification.idempotent.ttl-minutes:10}")
    private long notificationIdempotentTtlMinutes = 10;

    @Value("${price-tracker.price-refresh.batch-size:100}")
    private int priceRefreshBatchSize = DEFAULT_BATCH_SIZE;

    @Override
    @Transactional
    public void refreshProductPrice(Long productId) {
        refreshProductPriceInternal(productId);
    }

    private int refreshProductPriceInternal(Long productId) {
        Product product = getActiveProductOrThrow(productId);
        BigDecimal oldPrice = product.getCurrentPrice() == null ? DEFAULT_PRICE : product.getCurrentPrice();
        PriceProvider priceProvider = priceProviderRouter.route(product);
        PriceQuote quote = priceProvider.fetchPrice(product);
        BigDecimal newPrice = quote.price();
        LocalDateTime capturedAt = quote.capturedAt();
        product.setCurrency(quote.currency());
        product.setLastCheckedAt(capturedAt);
        if (newPrice.compareTo(oldPrice) == 0) {
            productMapper.updateById(product);
            clearProductCache(productId);
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
        return notificationTriggeredCount;
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
        for (int attempt = 0; attempt <= MAX_REFRESH_RETRIES; attempt++) {
            try {
                return refreshProductPriceInternal(productId);
            } catch (RuntimeException exception) {
                lastException = exception;
                log.warn("price refresh attempt failed, productId={}, attempt={}, maxRetries={}, message={}",
                        productId, attempt + 1, MAX_REFRESH_RETRIES, exception.getMessage());
            }
        }
        throw lastException;
    }

    private int resolveBatchSize() {
        return priceRefreshBatchSize > 0 ? priceRefreshBatchSize : DEFAULT_BATCH_SIZE;
    }

    private boolean shouldNotify(Watchlist watchlist, BigDecimal newPrice) {
        return watchlist.getTargetPrice() != null && newPrice.compareTo(watchlist.getTargetPrice()) <= 0;
    }

    private PriceAlertMessage buildPriceAlertMessage(Product product, Watchlist watchlist, BigDecimal newPrice, LocalDateTime now) {
        return PriceAlertMessage.builder()
                .messageId(buildMessageId(product.getId(), watchlist.getId()))
                .eventKey(PriceAlertEventKeyBuilder.buildTargetPriceReachedKey(
                        watchlist.getUserId(),
                        product.getId(),
                        watchlist.getId(),
                        watchlist.getTargetPrice(),
                        newPrice,
                        now))
                .userId(watchlist.getUserId())
                .productId(product.getId())
                .watchlistId(watchlist.getId())
                .currentPrice(newPrice)
                .targetPrice(watchlist.getTargetPrice())
                .productName(product.getProductName())
                .triggeredAt(now)
                .build();
    }

    private String buildMessageId(Long productId, Long watchlistId) {
        return productId + "-" + watchlistId + "-" + UUID.randomUUID();
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
        priceAlertProducer.send(buildPriceAlertMessage(product, watchlist, newPrice, now));
        return true;
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
