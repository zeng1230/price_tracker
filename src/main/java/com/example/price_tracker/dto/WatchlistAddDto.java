package com.example.price_tracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "Add to watchlist request payload")
@Data
public class WatchlistAddDto {

    @Schema(description = "ID of the product to watch", example = "1")
    @NotNull(message = "productId must not be null")
    private Long productId;

    @Schema(description = "User's target price to trigger notification", example = "800.00")
    @NotNull(message = "targetPrice must not be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "targetPrice must be greater than 0")
    private BigDecimal targetPrice;

    @Schema(description = "Enable status of notifications: 1 enabled, 0 disabled", example = "1")
    @NotNull(message = "notifyEnabled must not be null")
    private Integer notifyEnabled;
}
