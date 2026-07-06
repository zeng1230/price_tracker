package com.example.price_tracker.controller;

import com.example.price_tracker.common.PageResult;
import com.example.price_tracker.config.JwtProperties;
import com.example.price_tracker.entity.NotificationDelivery;
import com.example.price_tracker.entity.OutboxEvent;
import com.example.price_tracker.entity.UserRole;
import com.example.price_tracker.exception.GlobalExceptionHandler;
import com.example.price_tracker.interceptor.AuthInterceptor;
import com.example.price_tracker.service.AdminService;
import com.example.price_tracker.service.PriceService;
import com.example.price_tracker.util.JwtTokenUtil;
import com.example.price_tracker.vo.ProductPageVo;
import com.example.price_tracker.vo.UserVo;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerTest {

    private final AdminService adminService = mock(AdminService.class);
    private final PriceService priceService = mock(PriceService.class);
    private final JwtTokenUtil jwtTokenUtil = new JwtTokenUtil(jwtProperties());
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AdminController(adminService, priceService))
            .addInterceptors(new AuthInterceptor(jwtTokenUtil))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void unauthenticatedRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void userRequestReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("admin role required"));

        verify(adminService, never()).pageUsers(any(), any(), any());
    }

    @Test
    void adminCanQueryUsersAndProducts() throws Exception {
        when(adminService.pageUsers(1L, 10L, null)).thenReturn(PageResult.of(List.of(
                UserVo.builder().id(1L).username("alice").role(UserRole.USER).status(1).build()
        ), 1L, 1L, 10L));
        when(adminService.pageProducts(1L, 10L, null)).thenReturn(PageResult.of(List.of(
                ProductPageVo.builder().id(2L).productName("Laptop").status(0).build()
        ), 1L, 1L, 10L));

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].role").value("USER"));
        mockMvc.perform(get("/api/admin/products").header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].status").value(0));
    }

    @Test
    void adminCanUpdateProductStatus() throws Exception {
        mockMvc.perform(put("/api/admin/products/3/status")
                        .header("Authorization", bearer(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(adminService).updateProductStatus(3L, 1);
    }

    @Test
    void invalidProductStatusIsRejected() throws Exception {
        mockMvc.perform(put("/api/admin/products/3/status")
                        .header("Authorization", bearer(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(422));
    }

    @Test
    void adminRefreshDelegatesToExistingPriceService() throws Exception {
        mockMvc.perform(post("/api/admin/products/3/refresh-price")
                        .header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(priceService).refreshProductPrice(3L);
    }

    @Test
    void adminCanQueryAndRetryDeadOperationalTasks() throws Exception {
        when(adminService.listDeadOutboxEvents(50)).thenReturn(List.of(OutboxEvent.builder().id(10L).eventKey("outbox-event").build()));
        when(adminService.listDeadNotificationDeliveries(50)).thenReturn(List.of(NotificationDelivery.builder().id(20L).eventKey("delivery-event").build()));

        mockMvc.perform(get("/api/admin/outbox/dead").header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eventKey").value("outbox-event"));
        mockMvc.perform(post("/api/admin/outbox/10/retry").header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/admin/notification-deliveries/dead").header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eventKey").value("delivery-event"));
        mockMvc.perform(post("/api/admin/notification-deliveries/20/retry").header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(adminService).retryDeadOutboxEvent(10L);
        verify(adminService).retryDeadNotificationDelivery(20L);
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
