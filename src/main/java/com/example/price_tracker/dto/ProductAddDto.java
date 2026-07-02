package com.example.price_tracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "Product creation request payload")
@Data
public class ProductAddDto {

    @Schema(description = "Name of the product", example = "iPhone 15 Pro")
    @NotBlank(message = "productName must not be blank")
    @Size(max = 255, message = "productName length must be less than or equal to 255")
    private String productName;

    @NotBlank(message = "productUrl must not be blank")
    @Size(max = 500, message = "productUrl length must be less than or equal to 500")
    private String productUrl;

    @Schema(description = "E-commerce platform name", example = "amazon")
    @NotBlank(message = "platform must not be blank")
    @Size(max = 50, message = "platform length must be less than or equal to 50")
    private String platform = "amazon";

    @Schema(description = "Current price of the product", example = "999.99")
    @DecimalMin(value = "0.0", inclusive = false, message = "currentPrice must be greater than 0")
    private BigDecimal currentPrice;

    @NotBlank(message = "currency must not be blank")
    @Size(max = 10, message = "currency length must be less than or equal to 10")
    private String currency = "USD";

    @Size(max = 500, message = "imageUrl length must be less than or equal to 500")
    private String imageUrl;
}
