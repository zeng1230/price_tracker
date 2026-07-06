package com.example.price_tracker.util;

import com.example.price_tracker.annotation.AdminRequired;
import com.example.price_tracker.config.JwtProperties;
import com.example.price_tracker.context.UserContext;
import com.example.price_tracker.entity.UserRole;
import com.example.price_tracker.exception.BusinessException;
import com.example.price_tracker.interceptor.AuthInterceptor;
import com.example.price_tracker.service.JwtTokenBlacklistService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenUtilTest {

    private final JwtTokenUtil jwtTokenUtil = new JwtTokenUtil(jwtProperties());

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createsAndParsesAccessToken() {
        String token = jwtTokenUtil.generateAccessToken(7L, "alice", UserRole.ADMIN);

        JwtTokenUtil.TokenPayload payload = jwtTokenUtil.parseAccessToken(token);

        assertEquals(7L, payload.userId());
        assertEquals("alice", payload.username());
        assertEquals(UserRole.ADMIN, payload.role());
        assertTrue(payload.jti() != null && !payload.jti().isBlank());
    }

    @Test
    void interceptorRejectsBlacklistedToken() {
        String token = jwtTokenUtil.generateAccessToken(9L, "bob", UserRole.USER);
        JwtTokenBlacklistService blacklistService = mock(JwtTokenBlacklistService.class);
        when(blacklistService.isBlacklisted(token)).thenReturn(true);
        AuthInterceptor interceptor = new AuthInterceptor(jwtTokenUtil);
        interceptor.setJwtTokenBlacklistService(blacklistService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), mock())
        );

        assertEquals(401, exception.getCode());
    }

    @Test
    void interceptorExtractsBearerTokenAndStoresCurrentUser() throws Exception {
        String token = jwtTokenUtil.generateAccessToken(9L, "bob", UserRole.USER);
        AuthInterceptor interceptor = new AuthInterceptor(jwtTokenUtil);
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer " + token);

        boolean allowed = interceptor.preHandle(request, response, mock());

        assertTrue(allowed);
        assertEquals(9L, UserContext.getCurrentUserId());
        assertEquals("bob", UserContext.getCurrentUsername());
        assertEquals(UserRole.USER, UserContext.getCurrentUserRole());
        interceptor.afterCompletion(request, response, mock(), null);
        assertEquals(null, UserContext.getCurrentUserId());
        assertEquals(null, UserContext.getCurrentUserRole());
    }

    @Test
    void interceptorRejectsMissingAuthorizationHeader() {
        AuthInterceptor interceptor = new AuthInterceptor(jwtTokenUtil);
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpServletResponse response = new MockHttpServletResponse();

        assertThrows(BusinessException.class, () -> interceptor.preHandle(request, response, mock()));
    }

    @Test
    void rejectsTokenWithoutRoleClaim() {
        String token = buildToken(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> jwtTokenUtil.parseAccessToken(token)
        );

        assertEquals(401, exception.getCode());
    }

    @Test
    void rejectsIllegalRoleInsteadOfDowngradingToUser() {
        String token = buildToken("owner");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> jwtTokenUtil.parseAccessToken(token)
        );

        assertEquals(401, exception.getCode());
    }

    @Test
    void userCannotInvokeAdminRequiredHandler() throws Exception {
        AuthInterceptor interceptor = new AuthInterceptor(jwtTokenUtil);
        MockHttpServletRequest request = requestWithToken(UserRole.USER);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), adminHandler())
        );

        assertEquals(403, exception.getCode());
        assertEquals("admin role required", exception.getMessage());
    }

    @Test
    void adminCanInvokeAdminRequiredHandler() throws Exception {
        AuthInterceptor interceptor = new AuthInterceptor(jwtTokenUtil);

        boolean allowed = interceptor.preHandle(
                requestWithToken(UserRole.ADMIN),
                new MockHttpServletResponse(),
                adminHandler()
        );

        assertTrue(allowed);
    }

    private MockHttpServletRequest requestWithToken(UserRole role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer "
                + jwtTokenUtil.generateAccessToken(9L, "bob", role));
        return request;
    }

    private HandlerMethod adminHandler() throws NoSuchMethodException {
        Method method = AdminEndpoint.class.getDeclaredMethod("run");
        return new HandlerMethod(new AdminEndpoint(), method);
    }

    private String buildToken(String role) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .issuer("price-tracker-test")
                .subject("alice")
                .claim("userId", 7L);
        if (role != null) {
            builder.claim("role", role);
        }
        return builder
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(120, ChronoUnit.MINUTES)))
                .signWith(Keys.hmacShaKeyFor(
                        "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private static class AdminEndpoint {
        @AdminRequired
        public void run() {
        }
    }

    private JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("12345678901234567890123456789012");
        properties.setAccessTokenExpireMinutes(120L);
        properties.setRefreshTokenExpireDays(7L);
        properties.setIssuer("price-tracker-test");
        return properties;
    }
}
