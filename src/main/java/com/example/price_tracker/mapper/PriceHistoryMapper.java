package com.example.price_tracker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.price_tracker.dto.PriceTrendAggregateDto;
import com.example.price_tracker.entity.PriceHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface PriceHistoryMapper extends BaseMapper<PriceHistory> {

    @Select("""
            SELECT COUNT(*) AS history_count,
                   SUM(new_price) AS sum_new_price,
                   MIN(CASE
                           WHEN captured_at >= #{sevenDaysStart}
                           THEN LEAST(COALESCE(old_price, new_price), new_price)
                       END) AS window7_days_lowest_price,
                   MIN(CASE
                           WHEN captured_at >= #{thirtyDaysStart}
                           THEN LEAST(COALESCE(old_price, new_price), new_price)
                       END) AS window30_days_lowest_price,
                   MIN(LEAST(COALESCE(old_price, new_price), new_price)) AS historical_lowest_price,
                   MAX(GREATEST(COALESCE(old_price, new_price), new_price)) AS historical_highest_price,
                   MAX(captured_at) AS last_price_changed_at,
                   (
                       SELECT COALESCE(first_history.old_price, first_history.new_price)
                       FROM tb_price_history first_history
                       WHERE first_history.product_id = #{productId}
                       ORDER BY first_history.captured_at ASC, first_history.id ASC
                       LIMIT 1
                   ) AS first_old_price,
                   (
                       SELECT last_history.new_price
                       FROM tb_price_history last_history
                       WHERE last_history.product_id = #{productId}
                       ORDER BY last_history.captured_at DESC, last_history.id DESC
                       LIMIT 1
                   ) AS last_new_price
            FROM tb_price_history
            WHERE product_id = #{productId}
            """)
    PriceTrendAggregateDto selectPriceTrendAggregate(
            @Param("productId") Long productId,
            @Param("sevenDaysStart") LocalDateTime sevenDaysStart,
            @Param("thirtyDaysStart") LocalDateTime thirtyDaysStart);
}
