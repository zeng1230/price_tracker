package com.example.price_tracker.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Watchlist entry details")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistVo {

    @Schema(description = "Watchlist Entry ID", example = "5")
    private Long id;

    @Schema(description = "Product ID", example = "1")
    private Long productId;

    @Schema(description = "Product name", example = "iPhone 15 Pro")
    private String productName;

    private String productUrl;

    @Schema(description = "E-commerce platform name", example = "amazon")
    private String platform;

    @Schema(description = "Current price of the product", example = "999.99")
    private BigDecimal currentPrice;

    private String currency;

    private String imageUrl;

    @Schema(description = "User's target price to trigger notification", example = "800.00")
    private BigDecimal targetPrice;

    @Schema(description = "Enable status of notifications: 1 enabled, 0 disabled", example = "1")
    private Integer notifyEnabled;

    private BigDecimal lastNotifiedPrice;

    @Schema(description = "Watchlist entry creation time")
    private LocalDateTime createdAt;

    @Schema(description = "Watchlist entry update time")
    private LocalDateTime updatedAt;
}
