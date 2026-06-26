package com.example.price_tracker.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.price_tracker.common.PageResult;
import com.example.price_tracker.common.ResultCode;
import com.example.price_tracker.dto.PriceHistoryQueryDto;
import com.example.price_tracker.dto.PriceTrendAggregateDto;
import com.example.price_tracker.entity.PriceHistory;
import com.example.price_tracker.entity.Product;
import com.example.price_tracker.exception.BusinessException;
import com.example.price_tracker.mapper.PriceHistoryMapper;
import com.example.price_tracker.mapper.ProductMapper;
import com.example.price_tracker.service.PriceHistoryService;
import com.example.price_tracker.vo.PriceHistoryVo;
import com.example.price_tracker.vo.PriceTrendVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import static com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery;

@Service
@RequiredArgsConstructor
public class PriceHistoryServiceImpl implements PriceHistoryService {

    private static final int ACTIVE_STATUS = 1;
    private static final int MONEY_SCALE = 2;

    private final PriceHistoryMapper priceHistoryMapper;
    private final ProductMapper productMapper;

    @Override
    public PageResult<PriceHistoryVo> pageByProductId(Long productId, PriceHistoryQueryDto queryDto) {
        getActiveProductOrThrow(productId);
        Page<PriceHistory> page = priceHistoryMapper.selectPage(
                new Page<>(queryDto.getPageNum(), queryDto.getPageSize()),
                lambdaQuery(PriceHistory.class)
                        .eq(PriceHistory::getProductId, productId)
                        .orderByDesc(PriceHistory::getCapturedAt)
        );
        List<PriceHistoryVo> records = page.getRecords().stream()
                .map(this::toVo)
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PriceTrendVo getPriceTrend(Long productId) {
        Product product = getActiveProductOrThrow(productId);
        BigDecimal currentPrice = product.getCurrentPrice();
        if (currentPrice == null) {
            throw new BusinessException(
                    ResultCode.PRICE_NOT_AVAILABLE,
                    "current price is not available; refresh the product price first");
        }

        LocalDateTime now = LocalDateTime.now();
        PriceTrendAggregateDto aggregate = priceHistoryMapper.selectPriceTrendAggregate(
                productId,
                now.minusDays(7),
                now.minusDays(30));

        long historyCount = aggregate == null || aggregate.getHistoryCount() == null
                ? 0L
                : aggregate.getHistoryCount();
        if (historyCount == 0L) {
            BigDecimal normalizedCurrentPrice = money(currentPrice);
            return PriceTrendVo.builder()
                    .productId(productId)
                    .currency(product.getCurrency())
                    .currentPrice(normalizedCurrentPrice)
                    .lowestPrice7Days(normalizedCurrentPrice)
                    .lowestPrice30Days(normalizedCurrentPrice)
                    .historicalLowestPrice(normalizedCurrentPrice)
                    .historicalHighestPrice(normalizedCurrentPrice)
                    .averagePrice(normalizedCurrentPrice)
                    .priceChangeCount(0L)
                    .differenceFromLowest(BigDecimal.ZERO.setScale(MONEY_SCALE))
                    .differenceFromLowestPercentage(BigDecimal.ZERO.setScale(MONEY_SCALE))
                    .lastPriceChangedAt(null)
                    .build();
        }

        boolean appendCurrentPrice = aggregate.getLastNewPrice() == null
                || currentPrice.compareTo(aggregate.getLastNewPrice()) != 0;
        BigDecimal sampleSum = aggregate.getFirstOldPrice()
                .add(aggregate.getSumNewPrice());
        long sampleCount = historyCount + 1L;
        if (appendCurrentPrice) {
            sampleSum = sampleSum.add(currentPrice);
            sampleCount++;
        }

        BigDecimal historicalLowestPrice = min(aggregate.getHistoricalLowestPrice(), currentPrice);
        BigDecimal historicalHighestPrice = max(aggregate.getHistoricalHighestPrice(), currentPrice);
        BigDecimal lowestPrice7Days = min(aggregate.getWindow7DaysLowestPrice(), currentPrice);
        BigDecimal lowestPrice30Days = min(aggregate.getWindow30DaysLowestPrice(), currentPrice);
        BigDecimal differenceFromLowest = currentPrice.subtract(historicalLowestPrice);
        BigDecimal differencePercentage = historicalLowestPrice.compareTo(BigDecimal.ZERO) == 0
                ? null
                : differenceFromLowest
                        .multiply(BigDecimal.valueOf(100))
                        .divide(historicalLowestPrice, MONEY_SCALE, RoundingMode.HALF_UP);

        return PriceTrendVo.builder()
                .productId(productId)
                .currency(product.getCurrency())
                .currentPrice(money(currentPrice))
                .lowestPrice7Days(money(lowestPrice7Days))
                .lowestPrice30Days(money(lowestPrice30Days))
                .historicalLowestPrice(money(historicalLowestPrice))
                .historicalHighestPrice(money(historicalHighestPrice))
                .averagePrice(sampleSum.divide(BigDecimal.valueOf(sampleCount), MONEY_SCALE, RoundingMode.HALF_UP))
                .priceChangeCount(historyCount)
                .differenceFromLowest(money(differenceFromLowest))
                .differenceFromLowestPercentage(differencePercentage)
                .lastPriceChangedAt(aggregate.getLastPriceChangedAt())
                .build();
    }

    private Product getActiveProductOrThrow(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null || product.getStatus() == null || product.getStatus() != ACTIVE_STATUS) {
            throw new BusinessException(ResultCode.NOT_FOUND, "product not found");
        }
        return product;
    }

    private BigDecimal min(BigDecimal aggregatePrice, BigDecimal currentPrice) {
        if (aggregatePrice == null) {
            return currentPrice;
        }
        return aggregatePrice.min(currentPrice);
    }

    private BigDecimal max(BigDecimal aggregatePrice, BigDecimal currentPrice) {
        if (aggregatePrice == null) {
            return currentPrice;
        }
        return aggregatePrice.max(currentPrice);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private PriceHistoryVo toVo(PriceHistory priceHistory) {
        return PriceHistoryVo.builder()
                .id(priceHistory.getId())
                .productId(priceHistory.getProductId())
                .oldPrice(priceHistory.getOldPrice())
                .newPrice(priceHistory.getNewPrice())
                .capturedAt(priceHistory.getCapturedAt())
                .source(priceHistory.getSource())
                .build();
    }
}
