package com.example.price_tracker.service.impl;

import com.example.price_tracker.dto.LoginDto;
import com.example.price_tracker.dto.RegisterDto;
import com.example.price_tracker.entity.User;
import com.example.price_tracker.entity.UserRole;
import com.example.price_tracker.mapper.UserMapper;
import com.example.price_tracker.service.UserService;
import com.example.price_tracker.util.JwtTokenUtil;
import com.example.price_tracker.vo.UserVo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenUtil jwtTokenUtil;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void registerAlwaysCreatesUserRole() {
        RegisterDto dto = new RegisterDto();
        dto.setUsername("alice");
        dto.setPassword("password123");
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userService.toUserVo(any(User.class))).thenReturn(UserVo.builder()
                .username("alice")
                .role(UserRole.USER)
                .build());

        UserVo result = authService.register(dto);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertEquals(UserRole.USER, captor.getValue().getRole());
        assertEquals(UserRole.USER, result.getRole());
    }

    @Test
    void loginSignsTokenWithStoredRole() {
        LoginDto dto = new LoginDto();
        dto.setUsername("admin");
        dto.setPassword("password123");
        User admin = User.builder()
                .id(5L)
                .username("admin")
                .password("encoded")
                .status(1)
                .role(UserRole.ADMIN)
                .build();
        when(userMapper.selectOne(any())).thenReturn(admin);
        when(passwordEncoder.matches("password123", "encoded")).thenReturn(true);
        when(jwtTokenUtil.generateAccessToken(5L, "admin", UserRole.ADMIN)).thenReturn("token");

        authService.login(dto);

        verify(jwtTokenUtil).generateAccessToken(5L, "admin", UserRole.ADMIN);
    }
}
