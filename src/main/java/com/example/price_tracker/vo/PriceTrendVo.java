package com.example.price_tracker.vo;

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
public class PriceTrendVo {

    private Long productId;
    private String currency;
    private BigDecimal currentPrice;
    private BigDecimal lowestPrice7Days;
    private BigDecimal lowestPrice30Days;
    private BigDecimal historicalLowestPrice;
    private BigDecimal historicalHighestPrice;
    private BigDecimal averagePrice;
    private Long priceChangeCount;
    private BigDecimal differenceFromLowest;
    private BigDecimal differenceFromLowestPercentage;
    private LocalDateTime lastPriceChangedAt;
}
