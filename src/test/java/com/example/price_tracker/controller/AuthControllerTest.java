package com.example.price_tracker.controller;

import com.example.price_tracker.common.ResultCode;
import com.example.price_tracker.dto.LoginDto;
import com.example.price_tracker.dto.RegisterDto;
import com.example.price_tracker.entity.UserRole;
import com.example.price_tracker.service.AuthService;
import com.example.price_tracker.vo.LoginVo;
import com.example.price_tracker.vo.UserVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthService authService = mock(AuthService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService)).build();

    @Test
    void registerReturnsStructuredUserInfo() throws Exception {
        UserVo userVo = UserVo.builder()
                .id(1L)
                .username("alice")
                .email("alice@example.com")
                .nickname("Alice")
                .role(UserRole.USER)
                .status(1)
                .build();
        when(authService.register(any(RegisterDto.class))).thenReturn(userVo);

        RegisterDto request = new RegisterDto();
        request.setUsername("alice");
        request.setPassword("password123");
        request.setEmail("alice@example.com");
        request.setNickname("Alice");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void loginReturnsTokenPayload() throws Exception {
        LoginVo loginVo = LoginVo.builder().token("jwt-token").build();
        when(authService.login(any(LoginDto.class))).thenReturn(loginVo);

        LoginDto request = new LoginDto();
        request.setUsername("alice");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.token").value("jwt-token"));
    }
}
