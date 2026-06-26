package com.example.price_tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceTrendAggregateDto {

    private Long historyCount;
    private BigDecimal firstOldPrice;
    private BigDecimal lastNewPrice;
    private BigDecimal sumNewPrice;
    private BigDecimal window7DaysLowestPrice;
    private BigDecimal window30DaysLowestPrice;
    private BigDecimal historicalLowestPrice;
    private BigDecimal historicalHighestPrice;
    private LocalDateTime lastPriceChangedAt;
}
