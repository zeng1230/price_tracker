package com.example.price_tracker.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.price_tracker.common.PageResult;
import com.example.price_tracker.common.ResultCode;
import com.example.price_tracker.context.UserContext;
import com.example.price_tracker.dto.NotificationQueryDto;
import com.example.price_tracker.entity.Notification;
import com.example.price_tracker.entity.Watchlist;
import com.example.price_tracker.exception.BusinessException;
import com.example.price_tracker.mapper.NotificationMapper;
import com.example.price_tracker.mapper.ProductMapper;
import com.example.price_tracker.mapper.WatchlistMapper;
import com.example.price_tracker.mq.message.PriceAlertMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private WatchlistMapper watchlistMapper;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUserId(99L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void markReadRejectsOtherUsersNotification() {
        when(notificationMapper.selectById(5L)).thenReturn(notificationOwnedByAnotherUser());

        BusinessException exception = assertThrows(BusinessException.class, () -> notificationService.markRead(5L));

        assertEquals(ResultCode.NOT_FOUND.getCode(), exception.getCode());
        assertEquals("notification not found", exception.getMessage());
    }

    @Test
    void pageNotificationsFiltersByCurrentUser() {
        Page<Notification> page = new Page<>(1, 10);
        page.setRecords(java.util.List.of());
        page.setTotal(0);
        when(notificationMapper.selectPageByUserId(any(Page.class), org.mockito.ArgumentMatchers.eq(99L)))
                .thenReturn(page);

        NotificationQueryDto query = new NotificationQueryDto();
        PageResult<?> result = notificationService.pageMyNotifications(query);

        assertEquals(0, result.getTotal());
        verify(notificationMapper).selectPageByUserId(any(Page.class), org.mockito.ArgumentMatchers.eq(99L));
    }

    @Test
    void consumePriceAlertCreatesNotificationAndUpdatesWatchlistWhenNotDuplicate() {
        when(notificationMapper.selectByEventKey(eventKey())).thenReturn(null);
        when(watchlistMapper.selectById(5L)).thenReturn(activeWatchlistWithoutDedupPrice());

        notificationService.consumePriceAlert(triggeredMessage());

        verify(notificationMapper).insert(argThat(createdNotification()));
        verify(watchlistMapper).updateById(argThat(updatedWatchlistLastNotifiedPrice()));
    }

    @Test
    void consumePriceAlertSkipsDuplicatePriceNotification() {
        when(notificationMapper.selectByEventKey(eventKey())).thenReturn(null);
        when(watchlistMapper.selectById(5L)).thenReturn(activeWatchlistWithDedupPrice());

        notificationService.consumePriceAlert(triggeredMessage());

        verify(notificationMapper, never()).insert(any(Notification.class));
        verify(watchlistMapper, never()).updateById(any(Watchlist.class));
    }

    @Test
    void consumePriceAlertSkipsNotificationWhenCurrentPriceIsAboveTarget() {
        when(notificationMapper.selectByEventKey(eventKey())).thenReturn(null);
        when(watchlistMapper.selectById(5L)).thenReturn(activeWatchlistWithoutDedupPrice());

        notificationService.consumePriceAlert(messageAboveTarget());

        verify(notificationMapper, never()).insert(any(Notification.class));
        verify(watchlistMapper, never()).updateById(any(Watchlist.class));
    }

    @Test
    void consumePriceAlertRejectsMessageWithoutEventKey() {
        PriceAlertMessage message = triggeredMessage();
        message.setEventKey(" ");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> notificationService.consumePriceAlert(message));

        assertEquals(ResultCode.BAD_REQUEST.getCode(), exception.getCode());
        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    @Test
    void consumePriceAlertSkipsWhenEventKeyAlreadyExists() {
        when(notificationMapper.selectByEventKey(eventKey())).thenReturn(existingNotification());

        notificationService.consumePriceAlert(triggeredMessage());

        verify(notificationMapper, never()).insert(any(Notification.class));
        verify(watchlistMapper, never()).updateById(any(Watchlist.class));
    }

    @Test
    void consumePriceAlertTreatsEventKeyDuplicateInsertAsIdempotentSuccess() {
        when(notificationMapper.selectByEventKey(eventKey())).thenReturn(null);
        when(watchlistMapper.selectById(5L)).thenReturn(activeWatchlistWithoutDedupPrice());
        when(notificationMapper.insert(any(Notification.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry for key 'ux_notification_event_key'"));

        notificationService.consumePriceAlert(triggeredMessage());

        verify(watchlistMapper, never()).updateById(any(Watchlist.class));
    }

    @Test
    void consumePriceAlertRethrowsDuplicateKeyThatIsNotEventKeyConflict() {
        when(notificationMapper.selectByEventKey(eventKey())).thenReturn(null);
        when(watchlistMapper.selectById(5L)).thenReturn(activeWatchlistWithoutDedupPrice());
        when(notificationMapper.insert(any(Notification.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry for key 'other_unique_key'"));

        assertThrows(DuplicateKeyException.class, () -> notificationService.consumePriceAlert(triggeredMessage()));
    }

    private Notification notificationOwnedByAnotherUser() {
        Notification notification = new Notification();
        notification.setId(5L);
        notification.setUserId(77L);
        notification.setIsRead(0);
        return notification;
    }

    private PriceAlertMessage triggeredMessage() {
        return PriceAlertMessage.builder()
                .messageId("msg-001")
                .eventKey(eventKey())
                .userId(99L)
                .productId(1L)
                .watchlistId(5L)
                .productName("Laptop")
                .currentPrice(new BigDecimal("79.00"))
                .targetPrice(new BigDecimal("80.00"))
                .build();
    }

    private PriceAlertMessage messageAboveTarget() {
        PriceAlertMessage message = triggeredMessage();
        message.setCurrentPrice(new BigDecimal("81.00"));
        return message;
    }

    private Watchlist activeWatchlistWithoutDedupPrice() {
        Watchlist watchlist = new Watchlist();
        watchlist.setId(5L);
        watchlist.setUserId(99L);
        watchlist.setProductId(1L);
        watchlist.setTargetPrice(new BigDecimal("80.00"));
        watchlist.setNotifyEnabled(1);
        watchlist.setStatus(1);
        return watchlist;
    }

    private Watchlist activeWatchlistWithDedupPrice() {
        Watchlist watchlist = activeWatchlistWithoutDedupPrice();
        watchlist.setLastNotifiedPrice(new BigDecimal("79.00"));
        return watchlist;
    }

    private ArgumentMatcher<Notification> createdNotification() {
        return notification -> notification.getUserId().equals(99L)
                && notification.getProductId().equals(1L)
                && notification.getWatchlistId().equals(5L)
                && eventKey().equals(notification.getEventKey())
                && "TARGET_PRICE_REACHED".equals(notification.getNotifyType())
                && notification.getContent().contains("Laptop")
                && notification.getIsRead() == 0
                && notification.getSendStatus() == 1
                && notification.getCreatedAt() != null
                && notification.getSentAt() != null;
    }

    private ArgumentMatcher<Watchlist> updatedWatchlistLastNotifiedPrice() {
        return watchlist -> watchlist.getId().equals(5L)
                && new BigDecimal("79.00").compareTo(watchlist.getLastNotifiedPrice()) == 0
                && watchlist.getUpdatedAt() != null;
    }

    private Notification existingNotification() {
        Notification notification = new Notification();
        notification.setId(100L);
        notification.setEventKey(eventKey());
        return notification;
    }

    private String eventKey() {
        return "TARGET_PRICE_REACHED:99:1:5:80.00:79.00:1782468930000";
    }
}
