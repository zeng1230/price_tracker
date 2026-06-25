package com.example.price_tracker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.price_tracker.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    @Select("""
            SELECT id, user_id, product_id, watchlist_id, notify_type, content,
                   is_read, send_status, created_at, sent_at
            FROM tb_notification
            WHERE user_id = #{userId}
            ORDER BY created_at DESC, id DESC
            """)
    Page<Notification> selectPageByUserId(Page<Notification> page, @Param("userId") Long userId);
}
