package com.example.price_tracker.util;

import com.example.price_tracker.config.JwtProperties;
import com.example.price_tracker.entity.UserRole;
import com.example.price_tracker.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static com.example.price_tracker.common.ResultCode.UNAUTHORIZED;

@Component
@RequiredArgsConstructor
public class JwtTokenUtil {

    private final JwtProperties jwtProperties;

    public JwtProperties getJwtProperties() {
        return jwtProperties;
    }

    public String generateAccessToken(Long userId, String username, UserRole role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(username)
                .id(UUID.randomUUID().toString())
                .claim("userId", userId)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(jwtProperties.getAccessTokenExpireMinutes(), ChronoUnit.MINUTES)))
                .signWith(secretKey())
                .compact();
    }

    public TokenPayload parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = claims.get("userId", Long.class);
            if (userId == null) {
                throw new BusinessException(UNAUTHORIZED, "token userId is missing");
            }
            if (StringUtils.isBlank(claims.getId())) {
                throw new BusinessException(UNAUTHORIZED, "token jti is missing");
            }
            UserRole role = UserRole.parse(claims.get("role", String.class));
            return new TokenPayload(userId, claims.getSubject(), role, claims.getId(), claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(UNAUTHORIZED, "invalid token");
        }
    }

    public String resolveToken(String authorizationHeader) {
        if (StringUtils.isBlank(authorizationHeader) || !StringUtils.startsWithIgnoreCase(authorizationHeader, "Bearer ")) {
            throw new BusinessException(UNAUTHORIZED, "authorization header is missing or invalid");
        }
        String token = authorizationHeader.substring(7).trim();
        if (StringUtils.isBlank(token)) {
            throw new BusinessException(UNAUTHORIZED, "authorization token is blank");
        }
        return token;
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public record TokenPayload(Long userId, String username, UserRole role, String jti, Instant expiresAt) {
    }
}
