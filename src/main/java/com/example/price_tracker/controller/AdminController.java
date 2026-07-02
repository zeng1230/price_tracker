package com.example.price_tracker.controller;

import com.example.price_tracker.annotation.AdminRequired;
import com.example.price_tracker.common.PageResult;
import com.example.price_tracker.common.Result;
import com.example.price_tracker.dto.ProductStatusUpdateDto;
import com.example.price_tracker.service.AdminService;
import com.example.price_tracker.service.PriceService;
import com.example.price_tracker.vo.ProductPageVo;
import com.example.price_tracker.vo.UserVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Administration", description = "Administrative operations (Requires JWT and ADMIN role)")
@Validated
@RestController
@AdminRequired
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final PriceService priceService;

    @Operation(summary = "Page Query Users", description = "Query users page by page with optional keyword search")
    @GetMapping("/users")
    public Result<PageResult<UserVo>> users(
            @Parameter(description = "Page number (1-based)") @RequestParam(defaultValue = "1") @Min(value = 1, message = "pageNum must be greater than 0") Long pageNum,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize must be greater than 0") Long pageSize,
            @Parameter(description = "Keyword to filter by username/nickname") @RequestParam(required = false) String keyword) {
        return Result.success(adminService.pageUsers(pageNum, pageSize, keyword));
    }

    @Operation(summary = "Page Query All Products", description = "Query all products page by page with optional keyword search")
    @GetMapping("/products")
    public Result<PageResult<ProductPageVo>> products(
            @Parameter(description = "Page number (1-based)") @RequestParam(defaultValue = "1") @Min(value = 1, message = "pageNum must be greater than 0") Long pageNum,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize must be greater than 0") Long pageSize,
            @Parameter(description = "Keyword to filter by product name") @RequestParam(required = false) String keyword) {
        return Result.success(adminService.pageProducts(pageNum, pageSize, keyword));
    }

    @Operation(summary = "Update Product Status", description = "Enable or disable a product by ID")
    @PutMapping("/products/{productId}/status")
    public Result<Void> updateProductStatus(
            @Parameter(description = "Product ID") @PathVariable @Min(value = 1, message = "productId must be greater than 0") Long productId,
            @Valid @RequestBody ProductStatusUpdateDto request) {
        adminService.updateProductStatus(productId, request.getStatus());
        return Result.success();
    }

    @Operation(summary = "Admin Trigger Price Refresh", description = "Manually trigger a price refresh and alert check for a product")
    @PostMapping("/products/{productId}/refresh-price")
    public Result<Void> refreshProductPrice(
            @Parameter(description = "Product ID") @PathVariable @Min(value = 1, message = "productId must be greater than 0") Long productId) {
        priceService.refreshProductPrice(productId);
        return Result.success();
    }
}
