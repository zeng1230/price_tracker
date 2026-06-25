package com.example.price_tracker.context;

import com.example.price_tracker.entity.UserRole;

public final class UserContext {

    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<UserRole> CURRENT_USER_ROLE = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setCurrentUserId(Long userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static Long getCurrentUserId() {
        return CURRENT_USER_ID.get();
    }

    public static void setCurrentUsername(String username) {
        CURRENT_USERNAME.set(username);
    }

    public static String getCurrentUsername() {
        return CURRENT_USERNAME.get();
    }

    public static void setCurrentUserRole(UserRole role) {
        CURRENT_USER_ROLE.set(role);
    }

    public static UserRole getCurrentUserRole() {
        return CURRENT_USER_ROLE.get();
    }

    public static void clear() {
        CURRENT_USER_ID.remove();
        CURRENT_USERNAME.remove();
        CURRENT_USER_ROLE.remove();
    }
}
