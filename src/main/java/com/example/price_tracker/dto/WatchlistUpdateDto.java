package com.example.price_tracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "Update watchlist entry request payload")
@Data
public class WatchlistUpdateDto {

    @Schema(description = "User's updated target price to trigger notification", example = "750.00")
    @NotNull(message = "targetPrice must not be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "targetPrice must be greater than 0")
    private BigDecimal targetPrice;

    @Schema(description = "Enable status of notifications: 1 enabled, 0 disabled", example = "1")
    @NotNull(message = "notifyEnabled must not be null")
    private Integer notifyEnabled;
}
