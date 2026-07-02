package com.example.price_tracker.mq.message;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "RabbitMQ Price Alert Message Schema")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceAlertMessage {

    @Schema(description = "Unique message ID", example = "msg-001")
    private String messageId;

    @Schema(description = "Business unique event key", example = "TARGET_PRICE_REACHED:1001:2002:100.00:95.00:1782468930000")
    private String eventKey;

    @Schema(description = "Event schema version", example = "1")
    @Builder.Default
    private Integer eventVersion = 1;

    @Schema(description = "User ID", example = "1001")
    private Long userId;

    @Schema(description = "Product ID", example = "2002")
    private Long productId;

    @Schema(description = "Watchlist Entry ID", example = "5")
    private Long watchlistId;

    @Schema(description = "Current price when alert was triggered", example = "95.00")
    private BigDecimal currentPrice;

    @Schema(description = "Target price set by user", example = "100.00")
    private BigDecimal targetPrice;

    private String productName;

    private LocalDateTime triggeredAt;
}
