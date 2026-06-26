package com.example.price_tracker.service;

import com.example.price_tracker.common.PageResult;
import com.example.price_tracker.dto.PriceHistoryQueryDto;
import com.example.price_tracker.vo.PriceHistoryVo;
import com.example.price_tracker.vo.PriceTrendVo;

public interface PriceHistoryService {

    PageResult<PriceHistoryVo> pageByProductId(Long productId, PriceHistoryQueryDto queryDto);

    PriceTrendVo getPriceTrend(Long productId);
}
