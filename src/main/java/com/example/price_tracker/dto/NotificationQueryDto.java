package com.example.price_tracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Schema(description = "Notification query parameters payload")
@Data
public class NotificationQueryDto {

    @Schema(description = "Page number (1-based)", example = "1")
    @Min(value = 1, message = "pageNum must be greater than or equal to 1")
    private Long pageNum = 1L;

    @Schema(description = "Page size", example = "10")
    @Min(value = 1, message = "pageSize must be greater than or equal to 1")
    private Long pageSize = 10L;
}
