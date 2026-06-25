package com.example.price_tracker.interceptor;

import com.example.price_tracker.annotation.AdminRequired;
import com.example.price_tracker.common.ResultCode;
import com.example.price_tracker.context.UserContext;
import com.example.price_tracker.entity.UserRole;
import com.example.price_tracker.exception.BusinessException;
import com.example.price_tracker.util.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtTokenUtil jwtTokenUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorizationHeader = request.getHeader("Authorization");
        String token = jwtTokenUtil.resolveToken(authorizationHeader);
        JwtTokenUtil.TokenPayload payload = jwtTokenUtil.parseAccessToken(token);
        UserContext.setCurrentUserId(payload.userId());
        UserContext.setCurrentUsername(payload.username());
        UserContext.setCurrentUserRole(payload.role());
        if (requiresAdmin(handler) && payload.role() != UserRole.ADMIN) {
            UserContext.clear();
            throw new BusinessException(ResultCode.FORBIDDEN, "admin role required");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private boolean requiresAdmin(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return false;
        }
        return handlerMethod.hasMethodAnnotation(AdminRequired.class)
                || handlerMethod.getBeanType().isAnnotationPresent(AdminRequired.class);
    }
}
