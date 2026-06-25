package com.example.price_tracker.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.price_tracker.common.ResultCode;
import com.example.price_tracker.dto.LoginDto;
import com.example.price_tracker.dto.RegisterDto;
import com.example.price_tracker.entity.User;
import com.example.price_tracker.entity.UserRole;
import com.example.price_tracker.exception.BusinessException;
import com.example.price_tracker.mapper.UserMapper;
import com.example.price_tracker.service.AuthService;
import com.example.price_tracker.service.UserService;
import com.example.price_tracker.util.JwtTokenUtil;
import com.example.price_tracker.vo.LoginVo;
import com.example.price_tracker.vo.UserVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;

    @Override
    public UserVo register(RegisterDto registerDto) {
        String username = registerDto.getUsername().trim();
        User existingUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("limit 1"));
        if (existingUser != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "username already exists");
        }

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(registerDto.getPassword()))
                .email(normalize(registerDto.getEmail()))
                .nickname(normalize(registerDto.getNickname()))
                .role(UserRole.USER)
                .status(1)
                .build();
        userMapper.insert(user);
        return userService.toUserVo(user);
    }

    @Override
    public LoginVo login(LoginDto loginDto) {
        String username = loginDto.getUsername().trim();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("limit 1"));
        if (user == null || !passwordEncoder.matches(loginDto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "username or password is incorrect");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "user is disabled");
        }
        String token = jwtTokenUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        return LoginVo.builder().token(token).build();
    }

    private String normalize(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }
}
