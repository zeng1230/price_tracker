package com.example.price_tracker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.price_tracker.entity.OutboxEvent;
import com.example.price_tracker.entity.OutboxEventStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    @Insert("""
            INSERT IGNORE INTO tb_outbox_event
                (event_key, event_type, payload, status, attempts, next_retry_at, last_error, created_at, updated_at)
            VALUES
                (#{event.eventKey}, #{event.eventType}, #{event.payload}, #{event.status},
                 #{event.attempts}, #{event.nextRetryAt}, #{event.lastError}, #{event.createdAt}, #{event.updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "event.id")
    int insertIgnore(@Param("event") OutboxEvent event);

    @Select("""
            <script>
            SELECT id, event_key, event_type, payload, status, attempts, next_retry_at, last_error, created_at, updated_at
            FROM tb_outbox_event
            WHERE status IN
            <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
            </foreach>
              AND next_retry_at &lt;= #{now}
            ORDER BY next_retry_at ASC, id ASC
            LIMIT #{limit}
            </script>
            """)
    List<OutboxEvent> selectReadyEvents(@Param("statuses") List<OutboxEventStatus> statuses,
                                        @Param("now") LocalDateTime now,
                                        @Param("limit") int limit);

    @Select("""
            SELECT id, event_key, event_type, payload, status, attempts, next_retry_at, last_error, created_at, updated_at
            FROM tb_outbox_event
            WHERE event_key = #{eventKey}
            LIMIT 1
            """)
    OutboxEvent selectByEventKey(@Param("eventKey") String eventKey);

    @Update("""
            UPDATE tb_outbox_event
            SET status = 'SENT', updated_at = #{now}
            WHERE id = #{id}
            """)
    int markSent(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE tb_outbox_event
            SET status = 'FAILED_RETRYABLE',
                attempts = #{attempts},
                next_retry_at = #{nextRetryAt},
                last_error = #{lastError},
                updated_at = #{now}
            WHERE id = #{id}
            """)
    int markRetryable(@Param("id") Long id,
                      @Param("attempts") Integer attempts,
                      @Param("nextRetryAt") LocalDateTime nextRetryAt,
                      @Param("lastError") String lastError,
                      @Param("now") LocalDateTime now);

    @Update("""
            UPDATE tb_outbox_event
            SET status = 'DEAD',
                attempts = #{attempts},
                last_error = #{lastError},
                updated_at = #{now}
            WHERE id = #{id}
            """)
    int markDead(@Param("id") Long id,
                 @Param("attempts") Integer attempts,
                 @Param("lastError") String lastError,
                 @Param("now") LocalDateTime now);
}
