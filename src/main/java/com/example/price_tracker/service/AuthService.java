package com.example.price_tracker.service;

import com.example.price_tracker.dto.LoginDto;
import com.example.price_tracker.dto.RegisterDto;
import com.example.price_tracker.vo.LoginVo;
import com.example.price_tracker.vo.UserVo;

public interface AuthService {

    UserVo register(RegisterDto registerDto);

    LoginVo login(LoginDto loginDto);

    void logout(String token);
}
