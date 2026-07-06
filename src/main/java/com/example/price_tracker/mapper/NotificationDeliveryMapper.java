package com.example.price_tracker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.price_tracker.entity.NotificationDelivery;
import com.example.price_tracker.entity.NotificationDeliveryStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NotificationDeliveryMapper extends BaseMapper<NotificationDelivery> {

    @Insert("""
            INSERT IGNORE INTO tb_notification_delivery
                (event_key, channel, payload, status, attempts, next_retry_at, last_error, created_at, updated_at)
            VALUES
                (#{delivery.eventKey}, #{delivery.channel}, #{delivery.payload}, #{delivery.status},
                 #{delivery.attempts}, #{delivery.nextRetryAt}, #{delivery.lastError}, #{delivery.createdAt}, #{delivery.updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "delivery.id")
    int insertIgnore(@Param("delivery") NotificationDelivery delivery);

    @Update("""
            <script>
            UPDATE tb_notification_delivery
            SET claim_owner = #{claimOwner},
                claimed_at = #{now},
                claimed_until = #{claimedUntil},
                updated_at = #{now}
            WHERE id IN (
                SELECT id FROM (
                    SELECT id
                    FROM tb_notification_delivery
                    WHERE status IN
                    <foreach collection="statuses" item="status" open="(" separator="," close=")">
                        #{status}
                    </foreach>
                      AND next_retry_at &lt;= #{now}
                      AND (claim_owner IS NULL OR claimed_until IS NULL OR claimed_until &lt;= #{now})
                    ORDER BY next_retry_at ASC, id ASC
                    LIMIT #{limit}
                ) ready_deliveries
            )
            </script>
            """)
    int claimReadyDeliveries(@Param("statuses") List<NotificationDeliveryStatus> statuses,
                             @Param("now") LocalDateTime now,
                             @Param("limit") int limit,
                             @Param("claimOwner") String claimOwner,
                             @Param("claimedUntil") LocalDateTime claimedUntil);

    @Select("""
            SELECT id, event_key, channel, payload, status, attempts, next_retry_at, last_error,
                   claim_owner, claimed_at, claimed_until, created_at, updated_at
            FROM tb_notification_delivery
            WHERE claim_owner = #{claimOwner}
              AND claimed_until > #{now}
              AND next_retry_at <= #{now}
            ORDER BY next_retry_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<NotificationDelivery> selectClaimedReadyDeliveries(@Param("claimOwner") String claimOwner,
                                                            @Param("now") LocalDateTime now,
                                                            @Param("limit") int limit);

    @Select("""
            SELECT id, event_key, channel, payload, status, attempts, next_retry_at, last_error,
                   claim_owner, claimed_at, claimed_until, created_at, updated_at
            FROM tb_notification_delivery
            WHERE status = 'DEAD'
            ORDER BY updated_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<NotificationDelivery> selectDeadDeliveries(@Param("limit") int limit);

    @Update("""
            UPDATE tb_notification_delivery
            SET status = 'SENT',
                claim_owner = NULL,
                claimed_at = NULL,
                claimed_until = NULL,
                updated_at = #{now}
            WHERE id = #{id}
            """)
    int markSent(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE tb_notification_delivery
            SET status = 'FAILED_RETRYABLE',
                attempts = #{attempts},
                next_retry_at = #{nextRetryAt},
                last_error = #{lastError},
                claim_owner = NULL,
                claimed_at = NULL,
                claimed_until = NULL,
                updated_at = #{now}
            WHERE id = #{id}
            """)
    int markRetryable(@Param("id") Long id,
                      @Param("attempts") Integer attempts,
                      @Param("nextRetryAt") LocalDateTime nextRetryAt,
                      @Param("lastError") String lastError,
                      @Param("now") LocalDateTime now);

    @Update("""
            UPDATE tb_notification_delivery
            SET status = 'DEAD',
                attempts = #{attempts},
                last_error = #{lastError},
                claim_owner = NULL,
                claimed_at = NULL,
                claimed_until = NULL,
                updated_at = #{now}
            WHERE id = #{id}
            """)
    int markDead(@Param("id") Long id,
                 @Param("attempts") Integer attempts,
                 @Param("lastError") String lastError,
                 @Param("now") LocalDateTime now);

    @Update("""
            UPDATE tb_notification_delivery
            SET status = 'PENDING',
                next_retry_at = #{now},
                last_error = NULL,
                claim_owner = NULL,
                claimed_at = NULL,
                claimed_until = NULL,
                updated_at = #{now}
            WHERE id = #{id}
              AND status = 'DEAD'
            """)
    int resetDeadForRetry(@Param("id") Long id, @Param("now") LocalDateTime now);
}
