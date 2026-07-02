package com.example.price_tracker.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Schema(description = "User notification details")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationVo {

    @Schema(description = "Notification ID", example = "50")
    private Long id;

    @Schema(description = "Product ID", example = "1")
    private Long productId;

    @Schema(description = "Watchlist Entry ID", example = "5")
    private Long watchlistId;

    @Schema(description = "Product name", example = "iPhone 15 Pro")
    private String productName;

    private String notifyType;

    private String content;

    @Schema(description = "Read status: 0 unread, 1 read", example = "0")
    private Integer isRead;

    private Integer sendStatus;

    @Schema(description = "Notification creation time")
    private LocalDateTime createdAt;

    @Schema(description = "Notification sent time")
    private LocalDateTime sentAt;
}
