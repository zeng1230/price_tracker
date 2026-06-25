package com.example.price_tracker.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.price_tracker.common.PageResult;
import com.example.price_tracker.common.ResultCode;
import com.example.price_tracker.entity.Product;
import com.example.price_tracker.entity.User;
import com.example.price_tracker.exception.BusinessException;
import com.example.price_tracker.mapper.ProductMapper;
import com.example.price_tracker.mapper.UserMapper;
import com.example.price_tracker.redis.RedisCacheService;
import com.example.price_tracker.redis.RedisKeyManager;
import com.example.price_tracker.service.AdminService;
import com.example.price_tracker.service.UserService;
import com.example.price_tracker.vo.ProductPageVo;
import com.example.price_tracker.vo.UserVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final UserService userService;
    private final RedisCacheService cacheService;

    @Override
    public PageResult<UserVo> pageUsers(Long pageNum, Long pageSize, String keyword) {
        Page<User> page = userMapper.selectAdminPage(new Page<>(pageNum, pageSize), normalizeKeyword(keyword));
        List<UserVo> records = page.getRecords().stream()
                .map(userService::toUserVo)
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PageResult<ProductPageVo> pageProducts(Long pageNum, Long pageSize, String keyword) {
        Page<Product> page = productMapper.selectAdminPage(new Page<>(pageNum, pageSize), normalizeKeyword(keyword));
        List<ProductPageVo> records = page.getRecords().stream()
                .map(this::toProductPageVo)
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public void updateProductStatus(Long productId, Integer status) {
        Product product = productMapper.selectAdminById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "product not found");
        }
        if (productMapper.updateStatusByAdmin(productId, status) != 1) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "product status update failed");
        }
        clearProductCache(productId);
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private ProductPageVo toProductPageVo(Product product) {
        return ProductPageVo.builder()
                .id(product.getId())
                .productName(product.getProductName())
                .productUrl(product.getProductUrl())
                .platform(product.getPlatform())
                .currentPrice(product.getCurrentPrice())
                .currency(product.getCurrency())
                .imageUrl(product.getImageUrl())
                .status(product.getStatus())
                .lastCheckedAt(product.getLastCheckedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    private void clearProductCache(Long productId) {
        cacheService.delete(RedisKeyManager.productDetailKey(productId));
        cacheService.delete(RedisKeyManager.productPriceKey(productId));
        cacheService.delete(RedisKeyManager.nullValueKey("product:detail:" + productId));
        cacheService.delete(RedisKeyManager.nullValueKey("product:price:" + productId));
    }
}
