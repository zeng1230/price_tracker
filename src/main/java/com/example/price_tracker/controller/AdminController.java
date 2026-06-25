package com.example.price_tracker.controller;

import com.example.price_tracker.annotation.AdminRequired;
import com.example.price_tracker.common.PageResult;
import com.example.price_tracker.common.Result;
import com.example.price_tracker.dto.ProductStatusUpdateDto;
import com.example.price_tracker.service.AdminService;
import com.example.price_tracker.service.PriceService;
import com.example.price_tracker.vo.ProductPageVo;
import com.example.price_tracker.vo.UserVo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@AdminRequired
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final PriceService priceService;

    @GetMapping("/users")
    public Result<PageResult<UserVo>> users(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "pageNum must be greater than 0") Long pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize must be greater than 0") Long pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminService.pageUsers(pageNum, pageSize, keyword));
    }

    @GetMapping("/products")
    public Result<PageResult<ProductPageVo>> products(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "pageNum must be greater than 0") Long pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize must be greater than 0") Long pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminService.pageProducts(pageNum, pageSize, keyword));
    }

    @PutMapping("/products/{productId}/status")
    public Result<Void> updateProductStatus(
            @PathVariable @Min(value = 1, message = "productId must be greater than 0") Long productId,
            @Valid @RequestBody ProductStatusUpdateDto request) {
        adminService.updateProductStatus(productId, request.getStatus());
        return Result.success();
    }

    @PostMapping("/products/{productId}/refresh-price")
    public Result<Void> refreshProductPrice(
            @PathVariable @Min(value = 1, message = "productId must be greater than 0") Long productId) {
        priceService.refreshProductPrice(productId);
        return Result.success();
    }
}
