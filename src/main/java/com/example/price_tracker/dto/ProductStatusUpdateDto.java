package com.example.price_tracker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductStatusUpdateDto {

    @NotNull(message = "status must not be null")
    @Min(value = 0, message = "status must be 0 or 1")
    @Max(value = 1, message = "status must be 0 or 1")
    private Integer status;
}
