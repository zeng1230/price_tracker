package com.example.price_tracker.controller;

import com.example.price_tracker.config.JwtProperties;
import com.example.price_tracker.entity.UserRole;
import com.example.price_tracker.exception.GlobalExceptionHandler;
import com.example.price_tracker.interceptor.AuthInterceptor;
import com.example.price_tracker.service.PriceHistoryService;
import com.example.price_tracker.service.ProductService;
import com.example.price_tracker.util.JwtTokenUtil;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductControllerAuthorizationTest {

    private final ProductService productService = mock(ProductService.class);
    private final PriceHistoryService priceHistoryService = mock(PriceHistoryService.class);
    private final JwtTokenUtil jwtTokenUtil = new JwtTokenUtil(jwtProperties());
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ProductController(productService, priceHistoryService))
            .addInterceptors(new AuthInterceptor(jwtTokenUtil))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void userCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProductJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verify(productService, never()).addProduct(any());
    }

    @Test
    void adminCanCreateProduct() throws Exception {
        when(productService.addProduct(any())).thenReturn(3L);

        mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProductJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(3));
    }

    @Test
    void userCannotDeleteProduct() throws Exception {
        mockMvc.perform(delete("/api/products/3")
                        .header("Authorization", bearer(UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verify(productService, never()).deleteProduct(3L);
    }

    private String validProductJson() {
        return """
                {
                  "productName": "Laptop",
                  "productUrl": "https://example.com/laptop",
                  "platform": "mock",
                  "currentPrice": 100.00,
                  "currency": "CNY"
                }
                """;
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
