package com.example.price_tracker.service.impl;

import com.example.price_tracker.redis.RedisCacheService;
import com.example.price_tracker.service.JwtTokenBlacklistService;
import com.example.price_tracker.util.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class JwtTokenBlacklistServiceImpl implements JwtTokenBlacklistService {

    private static final String KEY_PREFIX = "price-tracker:jwt:blacklist:";

    private final RedisCacheService cacheService;
    private final JwtTokenUtil jwtTokenUtil;

    @Override
    public void blacklist(String token) {
        JwtTokenUtil.TokenPayload payload = jwtTokenUtil.parseAccessToken(token);
        Duration ttl = Duration.between(Instant.now(), payload.expiresAt());
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        cacheService.set(key(payload.jti()), "1", ttl);
    }

    @Override
    public boolean isBlacklisted(String token) {
        JwtTokenUtil.TokenPayload payload = jwtTokenUtil.parseAccessToken(token);
        return cacheService.get(key(payload.jti()), String.class) != null;
    }

    private String key(String jti) {
        return KEY_PREFIX + jti;
    }
}
