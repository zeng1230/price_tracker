package com.example.price_tracker.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.price_tracker.common.PageResult;
import com.example.price_tracker.common.ResultCode;
import com.example.price_tracker.entity.Product;
import com.example.price_tracker.entity.User;
import com.example.price_tracker.entity.UserRole;
import com.example.price_tracker.exception.BusinessException;
import com.example.price_tracker.mapper.NotificationDeliveryMapper;
import com.example.price_tracker.mapper.OutboxEventMapper;
import com.example.price_tracker.mapper.ProductMapper;
import com.example.price_tracker.mapper.UserMapper;
import com.example.price_tracker.redis.RedisCacheService;
import com.example.price_tracker.redis.RedisKeyManager;
import com.example.price_tracker.service.UserService;
import com.example.price_tracker.vo.ProductPageVo;
import com.example.price_tracker.vo.UserVo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private OutboxEventMapper outboxEventMapper;

    @Mock
    private NotificationDeliveryMapper notificationDeliveryMapper;

    @Mock
    private UserService userService;

    @Mock
    private RedisCacheService cacheService;

    @InjectMocks
    private AdminServiceImpl adminService;

    @Test
    void pageUsersUsesAdminQueryAndNeverReturnsPassword() {
        Page<User> page = pageOf(User.builder()
                .id(1L)
                .username("alice")
                .password("encoded-secret")
                .role(UserRole.USER)
                .status(0)
                .build());
        when(userMapper.selectAdminPage(any(Page.class), any())).thenReturn(page);
        when(userService.toUserVo(any(User.class))).thenReturn(UserVo.builder()
                .id(1L).username("alice").role(UserRole.USER).status(0).build());

        PageResult<UserVo> result = adminService.pageUsers(1L, 10L, null);

        assertEquals(0, result.getRecords().get(0).getStatus());
    }

    @Test
    void pageProductsIncludesDisabledProducts() {
        Page<Product> page = pageOf(Product.builder().id(3L).productName("Disabled").status(0).build());
        when(productMapper.selectAdminPage(any(Page.class), any())).thenReturn(page);

        PageResult<ProductPageVo> result = adminService.pageProducts(1L, 10L, null);

        assertEquals(0, result.getRecords().get(0).getStatus());
    }

    @Test
    void adminCanReactivateDisabledProductUsingUnfilteredMapperMethods() {
        when(productMapper.selectAdminById(3L)).thenReturn(Product.builder().id(3L).status(0).build());
        when(productMapper.updateStatusByAdmin(3L, 1)).thenReturn(1);

        adminService.updateProductStatus(3L, 1);

        verify(productMapper).updateStatusByAdmin(3L, 1);
        verify(cacheService).delete(RedisKeyManager.productDetailKey(3L));
        verify(cacheService).delete(RedisKeyManager.productPriceKey(3L));
    }

    @Test
    void updateStatusRejectsMissingProduct() {
        when(productMapper.selectAdminById(99L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> adminService.updateProductStatus(99L, 1)
        );

        assertEquals(ResultCode.NOT_FOUND.getCode(), exception.getCode());
        verify(productMapper, never()).updateStatusByAdmin(any(), any());
    }

    @Test
    void retryDeadOutboxRejectsMissingDeadRecord() {
        when(outboxEventMapper.resetDeadForRetry(eq(8L), any())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () -> adminService.retryDeadOutboxEvent(8L));

        assertEquals(ResultCode.NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void retryDeadNotificationDeliveryRejectsMissingDeadRecord() {
        when(notificationDeliveryMapper.resetDeadForRetry(eq(9L), any())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () -> adminService.retryDeadNotificationDelivery(9L));

        assertEquals(ResultCode.NOT_FOUND.getCode(), exception.getCode());
    }

    @SafeVarargs
    private <T> Page<T> pageOf(T... records) {
        Page<T> page = new Page<>(1, 10);
        page.setRecords(List.of(records));
        page.setTotal(records.length);
        return page;
    }
}
