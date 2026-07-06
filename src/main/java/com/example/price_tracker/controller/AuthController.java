package com.example.price_tracker.controller;

import com.example.price_tracker.common.Result;
import com.example.price_tracker.dto.LoginDto;
import com.example.price_tracker.dto.RegisterDto;
import com.example.price_tracker.service.AuthService;
import com.example.price_tracker.vo.LoginVo;
import com.example.price_tracker.vo.UserVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "User registration and login operations")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "User Registration", description = "Register a new user account (public)")
    @PostMapping("/register")
    public Result<UserVo> register(@Valid @RequestBody RegisterDto registerDto) {
        return Result.success(authService.register(registerDto));
    }

    @Operation(summary = "User Login", description = "Authenticate credentials and issue JWT access token (public)")
    @PostMapping("/login")
    public Result<LoginVo> login(@Valid @RequestBody LoginDto loginDto) {
        return Result.success(authService.login(loginDto));
    }

    @Operation(summary = "User Logout", description = "Revoke the current JWT access token")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authorizationHeader) {
        authService.logout(resolveBearerToken(authorizationHeader));
        return Result.success();
    }

    private String resolveBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "";
        }
        return authorizationHeader.substring(7).trim();
    }
}
