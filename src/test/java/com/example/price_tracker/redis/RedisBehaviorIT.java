package com.example.price_tracker.redis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {
                RedisAutoConfiguration.class,
                com.example.price_tracker.config.RedisConfig.class,
                RedisCacheService.class,
                RedisRateLimiter.class
        },
        properties = {
                "spring.main.allow-bean-definition-overriding=true"
        }
)
@Testcontainers
@ActiveProfiles("it")
public class RedisBehaviorIT {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private RedisCacheService cacheService;

    @Autowired
    private RedisRateLimiter rateLimiter;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void verifyRedisReadWrite() {
        String key = "test:read-write-key";
        String value = "hello-redis";

        cacheService.set(key, value, Duration.ofMinutes(5));

        String fetched = cacheService.get(key, String.class);
        assertThat(fetched).isEqualTo(value);

        cacheService.delete(key);
        assertThat(cacheService.get(key, String.class)).isNull();
    }

    @Test
    void verifyRateLimitKeyAndTTL() {
        Long userId = 9999L;
        String apiPath = "/api/v1/products";
        int limit = 2;
        int windowSeconds = 10;

        // First attempt - sets the rate limit key and initializes the TTL
        boolean allowed1 = rateLimiter.isAllowed(userId, apiPath, limit, windowSeconds);
        assertThat(allowed1).isTrue();

        String key = RedisKeyManager.rateLimitKey(userId, apiPath);

        // Check key exists
        Boolean hasKey = redisTemplate.hasKey(key);
        assertThat(hasKey).isTrue();

        // Check TTL exists and is greater than 0
        Long ttl = redisTemplate.getExpire(key);
        assertThat(ttl).isGreaterThan(0);
        assertThat(ttl).isLessThanOrEqualTo(windowSeconds);

        // Second attempt
        boolean allowed2 = rateLimiter.isAllowed(userId, apiPath, limit, windowSeconds);
        assertThat(allowed2).isTrue();

        // Third attempt - should exceed limit
        boolean allowed3 = rateLimiter.isAllowed(userId, apiPath, limit, windowSeconds);
        assertThat(allowed3).isFalse();
    }

    @Test
    void verifyIdempotencyKeyAndTTL() {
        String uniqueId = "idemp-001";
        String key = RedisKeyManager.notificationIdempotentKey(uniqueId);
        Duration ttlDuration = Duration.ofMinutes(5);

        boolean acquired1 = cacheService.setIfAbsent(key, "1", ttlDuration);
        assertThat(acquired1).isTrue();

        boolean acquired2 = cacheService.setIfAbsent(key, "1", ttlDuration);
        assertThat(acquired2).isFalse();

        // Check TTL exists and is greater than 0
        Long ttl = redisTemplate.getExpire(key);
        assertThat(ttl).isGreaterThan(0);
        assertThat(ttl).isLessThanOrEqualTo(ttlDuration.toSeconds());
    }
}
