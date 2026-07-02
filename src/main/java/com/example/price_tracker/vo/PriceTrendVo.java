package com.example.price_tracker.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Product price trend statistics data")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceTrendVo {

    @Schema(description = "Product ID", example = "1")
    private Long productId;

    private String currency;

    @Schema(description = "Current price of the product", example = "999.99")
    private BigDecimal currentPrice;

    private BigDecimal lowestPrice7Days;

    private BigDecimal lowestPrice30Days;

    @Schema(description = "Historical lowest price recorded for this product", example = "899.99")
    private BigDecimal historicalLowestPrice;

    @Schema(description = "Historical highest price recorded for this product", example = "1199.99")
    private BigDecimal historicalHighestPrice;

    @Schema(description = "Historical average price of the product", example = "1015.50")
    private BigDecimal averagePrice;

    private Long priceChangeCount;

    private BigDecimal differenceFromLowest;

    private BigDecimal differenceFromLowestPercentage;

    @Schema(description = "Time of the last price change event")
    private LocalDateTime lastPriceChangedAt;
}
