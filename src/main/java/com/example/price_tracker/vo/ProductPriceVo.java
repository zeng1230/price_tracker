package com.example.price_tracker.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Product current price details")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPriceVo {

    @Schema(description = "Product ID", example = "1")
    private Long productId;

    @Schema(description = "Current price of the product", example = "999.99")
    private BigDecimal currentPrice;

    private String currency;

    @Schema(description = "Timestamp of the last price check")
    private LocalDateTime lastCheckedAt;
}
