package com.example.price_tracker.controller;

import com.example.price_tracker.annotation.AdminRequired;
import com.example.price_tracker.common.PageResult;
import com.example.price_tracker.common.Result;
import com.example.price_tracker.dto.PriceHistoryQueryDto;
import com.example.price_tracker.dto.ProductAddDto;
import com.example.price_tracker.dto.ProductUpdateDto;
import com.example.price_tracker.service.PriceHistoryService;
import com.example.price_tracker.service.ProductService;
import com.example.price_tracker.vo.PriceHistoryVo;
import com.example.price_tracker.vo.ProductDetailVo;
import com.example.price_tracker.vo.ProductPageVo;
import com.example.price_tracker.vo.ProductPriceVo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final PriceHistoryService priceHistoryService;

    @PostMapping
    @AdminRequired
    public Result<Long> addProduct(@Valid @RequestBody ProductAddDto productAddDto) {
        return Result.success(productService.addProduct(productAddDto));
    }

    @GetMapping("/{id}")
    public Result<ProductDetailVo> getProductDetail(@PathVariable @Min(value = 1, message = "id must be greater than 0") Long id) {
        return Result.success(productService.getProductDetail(id));
    }

    @GetMapping("/{id}/price")
    public Result<ProductPriceVo> getCurrentPrice(@PathVariable @Min(value = 1, message = "id must be greater than 0") Long id) {
        return Result.success(productService.getCurrentPrice(id));
    }

    @GetMapping
    public Result<PageResult<ProductPageVo>> pageProducts(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "pageNum must be greater than 0") Long pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize must be greater than 0") Long pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(productService.pageProducts(pageNum, pageSize, keyword));
    }

    @PutMapping("/{id}")
    @AdminRequired
    public Result<Void> updateProduct(
            @PathVariable @Min(value = 1, message = "id must be greater than 0") Long id,
            @Valid @RequestBody ProductUpdateDto productUpdateDto) {
        productService.updateProduct(id, productUpdateDto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @AdminRequired
    public Result<Void> deleteProduct(@PathVariable @Min(value = 1, message = "id must be greater than 0") Long id) {
        productService.deleteProduct(id);
        return Result.success();
    }

    @GetMapping("/{id}/price-history")
    public Result<PageResult<PriceHistoryVo>> priceHistory(
            @PathVariable("id") @Min(value = 1, message = "id must be greater than 0") Long productId,
            @Valid PriceHistoryQueryDto queryDto) {
        return Result.success(priceHistoryService.pageByProductId(productId, queryDto));
    }
}
