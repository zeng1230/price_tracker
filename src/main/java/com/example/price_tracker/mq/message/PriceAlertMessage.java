package com.example.price_tracker.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceAlertMessage {

    private String messageId;

    private String eventKey;

    private Long userId;

    private Long productId;

    private Long watchlistId;

    private BigDecimal currentPrice;

    private BigDecimal targetPrice;

    private String productName;

    private LocalDateTime triggeredAt;
}
