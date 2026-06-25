package com.example.price_tracker.controller;

import com.example.price_tracker.config.JwtProperties;
import com.example.price_tracker.entity.UserRole;
import com.example.price_tracker.exception.GlobalExceptionHandler;
import com.example.price_tracker.interceptor.AuthInterceptor;
import com.example.price_tracker.service.PriceService;
import com.example.price_tracker.util.JwtTokenUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalProductControllerTest {

    private final PriceService priceService = mock(PriceService.class);
    private final JwtTokenUtil jwtTokenUtil = new JwtTokenUtil(jwtProperties());
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new InternalProductController(priceService))
            .addInterceptors(new AuthInterceptor(jwtTokenUtil))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void adminRefreshPriceDelegatesToService() throws Exception {
        mockMvc.perform(post("/api/internal/products/3/refresh-price")
                        .header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(priceService).refreshProductPrice(3L);
    }

    @Test
    void userCannotUseInternalRefreshEndpoint() throws Exception {
        mockMvc.perform(post("/api/internal/products/3/refresh-price")
                        .header("Authorization", bearer(UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    private String bearer(UserRole role) {
        return "Bearer " + jwtTokenUtil.generateAccessToken(7L, "tester", role);
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
