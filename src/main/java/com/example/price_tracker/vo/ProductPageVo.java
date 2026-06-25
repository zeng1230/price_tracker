package com.example.price_tracker.vo;

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
public class ProductPageVo {

    private Long id;

    private String productName;

    private String productUrl;

    private String platform;

    private BigDecimal currentPrice;

    private String currency;

    private String imageUrl;

    private Integer status;

    private LocalDateTime lastCheckedAt;

    private LocalDateTime updatedAt;
}
