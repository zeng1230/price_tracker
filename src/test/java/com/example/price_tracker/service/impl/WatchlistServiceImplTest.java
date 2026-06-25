package com.example.price_tracker.service.impl;

import com.example.price_tracker.common.PageResult;
import com.example.price_tracker.context.UserContext;
import com.example.price_tracker.dto.WatchlistAddDto;
import com.example.price_tracker.dto.WatchlistQueryDto;
import com.example.price_tracker.entity.Product;
import com.example.price_tracker.entity.Watchlist;
import com.example.price_tracker.exception.BusinessException;
import com.example.price_tracker.mapper.ProductMapper;
import com.example.price_tracker.mapper.WatchlistMapper;
import com.example.price_tracker.redis.RedisCacheService;
import com.example.price_tracker.redis.RedisKeyManager;
import com.example.price_tracker.vo.WatchlistVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceImplTest {

    @Mock
    private WatchlistMapper watchlistMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private RedisCacheService cacheService;

    @InjectMocks
    private WatchlistServiceImpl watchlistService;

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUserId(99L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void addWatchlistReactivatesExistingDisabledRecord() {
        when(productMapper.selectById(1L)).thenReturn(activeProduct());
        when(watchlistMapper.selectOne(any())).thenReturn(existingDisabledWatchlist());

        Long id = watchlistService.addWatchlist(addDto());

        assertEquals(10L, id);
        verify(watchlistMapper).updateById(argThat(reactivatedWatchlist()));
        verify(cacheService).delete(RedisKeyManager.userWatchlistKey(99L));
    }

    @Test
    void pageMyWatchlistReturnsCachedResultBeforeDatabaseLookup() {
        PageResult<WatchlistVo> cached = PageResult.of(List.of(WatchlistVo.builder().id(7L).build()), 1L, 1L, 10L);
        when(cacheService.get(RedisKeyManager.userWatchlistKey(99L), PageResult.class)).thenReturn(cached);

        PageResult<WatchlistVo> result = watchlistService.pageMyWatchlist(queryDto());

        assertEquals(7L, result.getRecords().get(0).getId());
        verify(watchlistMapper, never()).selectPage(any(), any());
    }

    @Test
    void addWatchlistIgnoresDuplicateActiveRecordWithoutInsert() {
        when(productMapper.selectById(1L)).thenReturn(activeProduct());
        when(watchlistMapper.selectOne(any())).thenReturn(existingActiveWatchlist());

        Long id = watchlistService.addWatchlist(addDto());

        assertEquals(11L, id);
        verify(watchlistMapper, never()).insert(any(Watchlist.class));
        verify(watchlistMapper, never()).updateById(any(Watchlist.class));
        verify(cacheService, never()).delete(anyString());
    }

    @Test
    void updateWatchlistRejectsAnotherUsersRecord() {
        Watchlist watchlist = existingActiveWatchlist();
        watchlist.setUserId(77L);
        when(watchlistMapper.selectById(11L)).thenReturn(watchlist);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> watchlistService.updateWatchlist(11L, new com.example.price_tracker.dto.WatchlistUpdateDto())
        );

        assertEquals(404, exception.getCode());
        verify(watchlistMapper, never()).updateById(any(Watchlist.class));
    }

    @Test
    void deleteWatchlistRejectsAnotherUsersRecord() {
        Watchlist watchlist = existingActiveWatchlist();
        watchlist.setUserId(77L);
        when(watchlistMapper.selectById(11L)).thenReturn(watchlist);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> watchlistService.deleteWatchlist(11L)
        );

        assertEquals(404, exception.getCode());
        verify(watchlistMapper, never()).updateById(any(Watchlist.class));
    }

    private ArgumentMatcher<Watchlist> reactivatedWatchlist() {
        return watchlist -> watchlist.getId().equals(10L)
                && watchlist.getUserId().equals(99L)
                && watchlist.getProductId().equals(1L)
                && watchlist.getStatus() == 1
                && watchlist.getNotifyEnabled() == 1
                && new BigDecimal("88.00").compareTo(watchlist.getTargetPrice()) == 0
                && watchlist.getLastNotifiedPrice() == null;
    }

    private WatchlistAddDto addDto() {
        WatchlistAddDto dto = new WatchlistAddDto();
        dto.setProductId(1L);
        dto.setTargetPrice(new BigDecimal("88.00"));
        dto.setNotifyEnabled(1);
        return dto;
    }

    private WatchlistQueryDto queryDto() {
        WatchlistQueryDto dto = new WatchlistQueryDto();
        dto.setPageNum(1L);
        dto.setPageSize(10L);
        return dto;
    }

    private Product activeProduct() {
        Product product = new Product();
        product.setId(1L);
        product.setStatus(1);
        product.setCurrentPrice(new BigDecimal("100.00"));
        return product;
    }

    private Watchlist existingDisabledWatchlist() {
        Watchlist watchlist = new Watchlist();
        watchlist.setId(10L);
        watchlist.setUserId(99L);
        watchlist.setProductId(1L);
        watchlist.setStatus(0);
        watchlist.setNotifyEnabled(0);
        watchlist.setTargetPrice(new BigDecimal("120.00"));
        return watchlist;
    }

    private Watchlist existingActiveWatchlist() {
        Watchlist watchlist = existingDisabledWatchlist();
        watchlist.setId(11L);
        watchlist.setStatus(1);
        return watchlist;
    }
}
