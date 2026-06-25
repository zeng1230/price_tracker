package com.example.price_tracker.entity;

import java.util.Locale;

public enum UserRole {
    USER,
    ADMIN;

    public static UserRole parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("role is missing");
        }
        return UserRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
