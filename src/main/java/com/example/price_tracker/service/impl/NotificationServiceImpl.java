package com.example.price_tracker.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.price_tracker.common.PageResult;
import com.example.price_tracker.common.ResultCode;
import com.example.price_tracker.context.UserContext;
import com.example.price_tracker.dto.NotificationQueryDto;
import com.example.price_tracker.entity.Notification;
import com.example.price_tracker.entity.Product;
import com.example.price_tracker.entity.Watchlist;
import com.example.price_tracker.exception.BusinessException;
import com.example.price_tracker.mapper.NotificationMapper;
import com.example.price_tracker.mapper.ProductMapper;
import com.example.price_tracker.mapper.WatchlistMapper;
import com.example.price_tracker.mq.message.PriceAlertMessage;
import com.example.price_tracker.service.NotificationService;
import com.example.price_tracker.vo.NotificationVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int ACTIVE_STATUS = 1;
    private static final int NOTIFY_ENABLED = 1;
    private static final int UNREAD = 0;
    private static final int SENT = 1;
    private static final String TARGET_PRICE_REACHED = "TARGET_PRICE_REACHED";

    private final NotificationMapper notificationMapper;
    private final ProductMapper productMapper;
    private final WatchlistMapper watchlistMapper;

    @Override
    public PageResult<NotificationVo> pageMyNotifications(NotificationQueryDto queryDto) {
        Long currentUserId = requireCurrentUserId();
        Page<Notification> page = notificationMapper.selectPageByUserId(
                new Page<>(queryDto.getPageNum(), queryDto.getPageSize()),
                currentUserId
        );
        Map<Long, Product> productMap = buildProductMap(page.getRecords());
        List<NotificationVo> records = page.getRecords().stream()
                .map(notification -> toVo(notification, productMap.get(notification.getProductId())))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public void markRead(Long id) {
        Long currentUserId = requireCurrentUserId();
        Notification notification = notificationMapper.selectById(id);
        if (notification == null || !currentUserId.equals(notification.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "notification not found");
        }
        if (notification.getIsRead() != null && notification.getIsRead() == 1) {
            return;
        }
        notification.setIsRead(1);
        notificationMapper.updateById(notification);
    }

    @Override
    @Transactional
    public void consumePriceAlert(PriceAlertMessage message) {
        validatePriceAlertMessage(message);
        Watchlist watchlist = watchlistMapper.selectById(message.getWatchlistId());
        if (!isWatchlistEligible(watchlist, message)) {
            log.info(
                    "Skip price alert notification, watchlistId={}, reason=watchlist_not_eligible",
                    message.getWatchlistId()
            );
            return;
        }
        if (isDuplicateNotification(watchlist.getLastNotifiedPrice(), message.getCurrentPrice())) {
            log.info(
                    "Skip duplicate price alert notification, watchlistId={}, currentPrice={}",
                    message.getWatchlistId(),
                    message.getCurrentPrice()
            );
            return;
        }
        LocalDateTime now = resolveTriggeredAt(message.getTriggeredAt());
        notificationMapper.insert(buildNotification(message, now));
        watchlist.setLastNotifiedPrice(message.getCurrentPrice());
        watchlist.setUpdatedAt(now);
        watchlistMapper.updateById(watchlist);
        log.info(
                "Created price alert notification, watchlistId={}, productId={}, userId={}",
                message.getWatchlistId(),
                message.getProductId(),
                message.getUserId()
        );
    }

    private Map<Long, Product> buildProductMap(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return Collections.emptyMap();
        }
        return productMapper.selectBatchIds(notifications.stream().map(Notification::getProductId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    private NotificationVo toVo(Notification notification, Product product) {
        return NotificationVo.builder()
                .id(notification.getId())
                .productId(notification.getProductId())
                .watchlistId(notification.getWatchlistId())
                .productName(product == null ? null : product.getProductName())
                .notifyType(notification.getNotifyType())
                .content(notification.getContent())
                .isRead(notification.getIsRead())
                .sendStatus(notification.getSendStatus())
                .createdAt(notification.getCreatedAt())
                .sentAt(notification.getSentAt())
                .build();
    }

    private Long requireCurrentUserId() {
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "current user is not authenticated");
        }
        return currentUserId;
    }

    private void validatePriceAlertMessage(PriceAlertMessage message) {
        if (message == null
                || message.getUserId() == null
                || message.getProductId() == null
                || message.getWatchlistId() == null
                || message.getCurrentPrice() == null
                || message.getTargetPrice() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "invalid price alert message");
        }
    }

    private boolean isWatchlistEligible(Watchlist watchlist, PriceAlertMessage message) {
        return watchlist != null
                && watchlist.getStatus() != null
                && watchlist.getStatus() == ACTIVE_STATUS
                && watchlist.getNotifyEnabled() != null
                && watchlist.getNotifyEnabled() == NOTIFY_ENABLED
                && message.getCurrentPrice().compareTo(message.getTargetPrice()) <= 0;
    }

    private boolean isDuplicateNotification(BigDecimal lastNotifiedPrice, BigDecimal currentPrice) {
        return lastNotifiedPrice != null && lastNotifiedPrice.compareTo(currentPrice) == 0;
    }

    private LocalDateTime resolveTriggeredAt(LocalDateTime triggeredAt) {
        return triggeredAt == null ? LocalDateTime.now() : triggeredAt;
    }

    private Notification buildNotification(PriceAlertMessage message, LocalDateTime now) {
        return Notification.builder()
                .userId(message.getUserId())
                .productId(message.getProductId())
                .watchlistId(message.getWatchlistId())
                .notifyType(TARGET_PRICE_REACHED)
                .content(message.getProductName() + " current price " + message.getCurrentPrice()
                        + " reached target " + message.getTargetPrice())
                .isRead(UNREAD)
                .sendStatus(SENT)
                .createdAt(now)
                .sentAt(now)
                .build();
    }
}
