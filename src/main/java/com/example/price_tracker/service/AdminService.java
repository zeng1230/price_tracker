package com.example.price_tracker.service;

import com.example.price_tracker.common.PageResult;
import com.example.price_tracker.entity.NotificationDelivery;
import com.example.price_tracker.entity.OutboxEvent;
import com.example.price_tracker.vo.ProductPageVo;
import com.example.price_tracker.vo.UserVo;

import java.util.List;

public interface AdminService {

    PageResult<UserVo> pageUsers(Long pageNum, Long pageSize, String keyword);

    PageResult<ProductPageVo> pageProducts(Long pageNum, Long pageSize, String keyword);

    void updateProductStatus(Long productId, Integer status);

    List<OutboxEvent> listDeadOutboxEvents(int limit);

    void retryDeadOutboxEvent(Long id);

    List<NotificationDelivery> listDeadNotificationDeliveries(int limit);

    void retryDeadNotificationDelivery(Long id);
}
