package com.example.price_tracker.service;

public interface JwtTokenBlacklistService {

    void blacklist(String token);

    boolean isBlacklisted(String token);
}
