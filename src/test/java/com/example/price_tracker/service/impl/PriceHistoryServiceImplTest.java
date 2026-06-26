package com.example.price_tracker.service.impl;

import com.example.price_tracker.common.ResultCode;
import com.example.price_tracker.dto.PriceTrendAggregateDto;
import com.example.price_tracker.entity.Product;
import com.example.price_tracker.exception.BusinessException;
import com.example.price_tracker.mapper.PriceHistoryMapper;
import com.example.price_tracker.mapper.ProductMapper;
import com.example.price_tracker.vo.PriceTrendVo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceHistoryServiceImplTest {

    @Mock
    private PriceHistoryMapper priceHistoryMapper;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private PriceHistoryServiceImpl priceHistoryService;

    @Test
    void shouldUseCurrentPriceAsOnlySampleWhenHistoryIsEmpty() {
        when(productMapper.selectById(1L)).thenReturn(activeProduct("100.00"));
        when(priceHistoryMapper.selectPriceTrendAggregate(
                eq(1L), isA(LocalDateTime.class), isA(LocalDateTime.class)))
                .thenReturn(PriceTrendAggregateDto.builder()
                        .historyCount(0L)
                        .build());

        PriceTrendVo result = priceHistoryService.getPriceTrend(1L);

        assertMoney("100.00", result.getCurrentPrice());
        assertMoney("100.00", result.getLowestPrice7Days());
        assertMoney("100.00", result.getLowestPrice30Days());
        assertMoney("100.00", result.getHistoricalLowestPrice());
        assertMoney("100.00", result.getHistoricalHighestPrice());
        assertMoney("100.00", result.getAveragePrice());
        assertMoney("0.00", result.getDifferenceFromLowest());
        assertMoney("0.00", result.getDifferenceFromLowestPercentage());
        assertEquals(0L, result.getPriceChangeCount());
        assertNull(result.getLastPriceChangedAt());
    }

    @Test
    void shouldCalculateAverageFromFirstOldPriceAndEveryNewPriceWithoutDuplicatingCurrentPrice() {
        when(productMapper.selectById(1L)).thenReturn(activeProduct("80.00"));
        when(priceHistoryMapper.selectPriceTrendAggregate(
                eq(1L), isA(LocalDateTime.class), isA(LocalDateTime.class)))
                .thenReturn(PriceTrendAggregateDto.builder()
                        .historyCount(2L)
                        .firstOldPrice(new BigDecimal("100.00"))
                        .lastNewPrice(new BigDecimal("80.00"))
                        .sumNewPrice(new BigDecimal("170.00"))
                        .window7DaysLowestPrice(new BigDecimal("80.00"))
                        .window30DaysLowestPrice(new BigDecimal("80.00"))
                        .historicalLowestPrice(new BigDecimal("80.00"))
                        .historicalHighestPrice(new BigDecimal("100.00"))
                        .lastPriceChangedAt(LocalDateTime.of(2026, 6, 24, 12, 0))
                        .build());

        PriceTrendVo result = priceHistoryService.getPriceTrend(1L);

        assertMoney("90.00", result.getAveragePrice());
        assertEquals(2L, result.getPriceChangeCount());
    }

    @Test
    void shouldAppendDifferentCurrentPriceAndIncludeItInAllMinMaxCalculations() {
        when(productMapper.selectById(1L)).thenReturn(activeProduct("70.00"));
        when(priceHistoryMapper.selectPriceTrendAggregate(
                eq(1L), isA(LocalDateTime.class), isA(LocalDateTime.class)))
                .thenReturn(PriceTrendAggregateDto.builder()
                        .historyCount(2L)
                        .firstOldPrice(new BigDecimal("100.00"))
                        .lastNewPrice(new BigDecimal("80.00"))
                        .sumNewPrice(new BigDecimal("170.00"))
                        .window7DaysLowestPrice(new BigDecimal("80.00"))
                        .window30DaysLowestPrice(new BigDecimal("80.00"))
                        .historicalLowestPrice(new BigDecimal("80.00"))
                        .historicalHighestPrice(new BigDecimal("100.00"))
                        .build());

        PriceTrendVo result = priceHistoryService.getPriceTrend(1L);

        assertMoney("85.00", result.getAveragePrice());
        assertMoney("70.00", result.getLowestPrice7Days());
        assertMoney("70.00", result.getLowestPrice30Days());
        assertMoney("70.00", result.getHistoricalLowestPrice());
        assertMoney("100.00", result.getHistoricalHighestPrice());
        assertMoney("0.00", result.getDifferenceFromLowest());
    }

    @Test
    void shouldFallBackWindowMinimumsToCurrentPriceWhenWindowHasNoHistory() {
        when(productMapper.selectById(1L)).thenReturn(activeProduct("90.00"));
        when(priceHistoryMapper.selectPriceTrendAggregate(
                eq(1L), isA(LocalDateTime.class), isA(LocalDateTime.class)))
                .thenReturn(PriceTrendAggregateDto.builder()
                        .historyCount(1L)
                        .firstOldPrice(new BigDecimal("100.00"))
                        .lastNewPrice(new BigDecimal("80.00"))
                        .sumNewPrice(new BigDecimal("80.00"))
                        .historicalLowestPrice(new BigDecimal("80.00"))
                        .historicalHighestPrice(new BigDecimal("100.00"))
                        .build());

        PriceTrendVo result = priceHistoryService.getPriceTrend(1L);

        assertMoney("90.00", result.getLowestPrice7Days());
        assertMoney("90.00", result.getLowestPrice30Days());
    }

    @Test
    void shouldRejectActiveProductWithoutCurrentPrice() {
        when(productMapper.selectById(1L)).thenReturn(activeProduct(null));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> priceHistoryService.getPriceTrend(1L));

        assertEquals(ResultCode.PRICE_NOT_AVAILABLE.getCode(), exception.getCode());
        assertEquals("current price is not available; refresh the product price first", exception.getMessage());
    }

    @Test
    void shouldReturnNullPercentageWhenHistoricalLowestPriceIsZero() {
        when(productMapper.selectById(1L)).thenReturn(activeProduct("10.00"));
        when(priceHistoryMapper.selectPriceTrendAggregate(
                eq(1L), isA(LocalDateTime.class), isA(LocalDateTime.class)))
                .thenReturn(PriceTrendAggregateDto.builder()
                        .historyCount(1L)
                        .firstOldPrice(BigDecimal.ZERO)
                        .lastNewPrice(new BigDecimal("10.00"))
                        .sumNewPrice(new BigDecimal("10.00"))
                        .window7DaysLowestPrice(BigDecimal.ZERO)
                        .window30DaysLowestPrice(BigDecimal.ZERO)
                        .historicalLowestPrice(BigDecimal.ZERO)
                        .historicalHighestPrice(new BigDecimal("10.00"))
                        .build());

        PriceTrendVo result = priceHistoryService.getPriceTrend(1L);

        assertNull(result.getDifferenceFromLowestPercentage());
    }

    private Product activeProduct(String currentPrice) {
        return Product.builder()
                .id(1L)
                .productName("Laptop")
                .currentPrice(currentPrice == null ? null : new BigDecimal(currentPrice))
                .currency("CNY")
                .status(1)
                .build();
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
