package com.example.price_tracker.controller;

import com.example.price_tracker.annotation.AdminRequired;
import com.example.price_tracker.common.Result;
import com.example.price_tracker.service.PriceService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/internal/products")
@RequiredArgsConstructor
public class InternalProductController {

    private final PriceService priceService;

    @PostMapping("/{id}/refresh-price")
    @AdminRequired
    public Result<Void> refreshPrice(@PathVariable("id") @Min(value = 1, message = "id must be greater than 0") Long productId) {
        priceService.refreshProductPrice(productId);
        return Result.success();
    }
}
