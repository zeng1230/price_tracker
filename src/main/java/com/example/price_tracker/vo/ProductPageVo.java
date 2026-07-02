package com.example.price_tracker.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Product page item data")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPageVo {

    @Schema(description = "Product ID", example = "1")
    private Long id;

    @Schema(description = "Name of the product", example = "iPhone 15 Pro")
    private String productName;

    private String productUrl;

    @Schema(description = "E-commerce platform name", example = "amazon")
    private String platform;

    @Schema(description = "Current price", example = "999.99")
    private BigDecimal currentPrice;

    private String currency;

    private String imageUrl;

    @Schema(description = "Product status: 1 active, 0 inactive", example = "1")
    private Integer status;

    private LocalDateTime lastCheckedAt;

    @Schema(description = "Product update time")
    private LocalDateTime updatedAt;
}
