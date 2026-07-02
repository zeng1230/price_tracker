package com.example.price_tracker.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Product price history record details")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceHistoryVo {

    @Schema(description = "Price history entry ID", example = "100")
    private Long id;

    @Schema(description = "Product ID", example = "1")
    private Long productId;

    @Schema(description = "Previous price of the product", example = "1099.99")
    private BigDecimal oldPrice;

    @Schema(description = "New/updated price of the product", example = "999.99")
    private BigDecimal newPrice;

    @Schema(description = "Time when this price change was captured")
    private LocalDateTime capturedAt;

    @Schema(description = "Source platform or mechanism that provided the price", example = "mock")
    private String source;
}
