package com.example.price_tracker.redis;

import com.example.price_tracker.common.ResultCode;
import com.example.price_tracker.context.UserContext;
import com.example.price_tracker.exception.BusinessException;
import com.example.price_tracker.metrics.PriceTrackerMetrics;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@Slf4j
public class RedisRateLimitAspect {

    private final RedisRateLimiter rateLimiter;
    private final RedisRateLimitProperties properties;
    private final PriceTrackerMetrics metrics;

    @Autowired
    public RedisRateLimitAspect(RedisRateLimiter rateLimiter, RedisRateLimitProperties properties, PriceTrackerMetrics metrics) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.metrics = metrics;
    }

    public RedisRateLimitAspect(RedisRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.properties = new RedisRateLimitProperties();
        this.metrics = null;
    }

    public RedisRateLimitAspect(RedisRateLimiter rateLimiter, PriceTrackerMetrics metrics) {
        this.rateLimiter = rateLimiter;
        this.properties = new RedisRateLimitProperties();
        this.metrics = metrics;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        Long userId = UserContext.getCurrentUserId();
        String apiPath = currentRequestPath();
        int limit = rateLimit.limit() > 0 ? rateLimit.limit() : properties.getDefaultLimit();
        int windowSeconds = rateLimit.windowSeconds() > 0
                ? rateLimit.windowSeconds()
                : properties.getDefaultWindowSeconds();

        if (!rateLimiter.isAllowed(userId, apiPath, limit, windowSeconds)) {
            log.info("rate limited, userId={}, apiPath={}, limit={}, windowSeconds={}",
                    userId, apiPath, limit, windowSeconds);
            if (metrics != null) {
                String method = currentRequestMethod();
                String lowCardApi = resolveLowCardinalityApi(method, apiPath);
                metrics.recordRateLimitBlock(lowCardApi);
            }
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "request too frequent");
        }
        return joinPoint.proceed();
    }

    private String currentRequestPath() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        return request.getRequestURI();
    }

    private String currentRequestMethod() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        return request.getMethod();
    }

    private String resolveLowCardinalityApi(String method, String uri) {
        if (uri != null) {
            if (uri.startsWith("/api/watchlist")) {
                if ("POST".equalsIgnoreCase(method)) {
                    return "watchlist_add";
                } else if ("PUT".equalsIgnoreCase(method)) {
                    return "watchlist_update";
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    return "watchlist_delete";
                }
            }
        }
        return "unknown";
    }
}
