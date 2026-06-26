package com.example.price_tracker.controller;

import com.example.price_tracker.common.PageResult;
import com.example.price_tracker.dto.ProductAddDto;
import com.example.price_tracker.dto.ProductUpdateDto;
import com.example.price_tracker.exception.GlobalExceptionHandler;
import com.example.price_tracker.service.PriceHistoryService;
import com.example.price_tracker.service.ProductService;
import com.example.price_tracker.vo.ProductDetailVo;
import com.example.price_tracker.vo.ProductPageVo;
import com.example.price_tracker.vo.ProductPriceVo;
import com.example.price_tracker.vo.PriceTrendVo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductControllerTest {

    private final ProductService productService = Mockito.mock(ProductService.class);
    private final PriceHistoryService priceHistoryService = Mockito.mock(PriceHistoryService.class);

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(productService, priceHistoryService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void shouldCreateProduct() throws Exception {
        when(productService.addProduct(org.mockito.ArgumentMatchers.any(ProductAddDto.class))).thenReturn(1L);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productName": "Kindle",
                                  "productUrl": "https://example.com/kindle",
                                  "platform": "amazon",
                                  "currentPrice": 129.99,
                                  "currency": "USD"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(1L));
    }

    @Test
    void shouldValidateCreateRequest() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productName": "",
                                  "productUrl": ""
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(422));
    }

    @Test
    void shouldGetProductDetail() throws Exception {
        ProductDetailVo detailVo = ProductDetailVo.builder()
                .id(1L)
                .productName("Kindle")
                .productUrl("https://example.com/kindle")
                .platform("amazon")
                .currentPrice(new BigDecimal("129.99"))
                .currency("USD")
                .build();
        when(productService.getProductDetail(1L)).thenReturn(detailVo);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.productName").value("Kindle"));
    }

    @Test
    void shouldGetCurrentProductPrice() throws Exception {
        ProductPriceVo priceVo = ProductPriceVo.builder()
                .productId(1L)
                .currentPrice(new BigDecimal("129.99"))
                .currency("USD")
                .build();
        when(productService.getCurrentPrice(1L)).thenReturn(priceVo);

        mockMvc.perform(get("/api/products/1/price"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(1L))
                .andExpect(jsonPath("$.data.currentPrice").value(129.99))
                .andExpect(jsonPath("$.data.currency").value("USD"));
    }

    @Test
    void shouldGetProductPriceTrend() throws Exception {
        when(priceHistoryService.getPriceTrend(1L)).thenReturn(PriceTrendVo.builder()
                .productId(1L)
                .currency("USD")
                .currentPrice(new BigDecimal("129.99"))
                .lowestPrice7Days(new BigDecimal("119.99"))
                .lowestPrice30Days(new BigDecimal("109.99"))
                .historicalLowestPrice(new BigDecimal("99.99"))
                .historicalHighestPrice(new BigDecimal("149.99"))
                .averagePrice(new BigDecimal("124.99"))
                .priceChangeCount(4L)
                .differenceFromLowest(new BigDecimal("30.00"))
                .differenceFromLowestPercentage(new BigDecimal("30.00"))
                .build());

        mockMvc.perform(get("/api/products/1/price-trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(1L))
                .andExpect(jsonPath("$.data.currentPrice").value(129.99))
                .andExpect(jsonPath("$.data.averagePrice").value(124.99))
                .andExpect(jsonPath("$.data.priceChangeCount").value(4L));

        verify(priceHistoryService).getPriceTrend(1L);
    }

    @Test
    void shouldPageProducts() throws Exception {
        PageResult<ProductPageVo> pageResult = PageResult.of(List.of(
                ProductPageVo.builder().id(1L).productName("Kindle").build()
        ), 1L, 1L, 10L);
        when(productService.pageProducts(1L, 10L, "Kin")).thenReturn(pageResult);

        mockMvc.perform(get("/api/products")
                        .param("pageNum", "1")
                        .param("pageSize", "10")
                        .param("keyword", "Kin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1L))
                .andExpect(jsonPath("$.data.records[0].productName").value("Kindle"));
    }

    @Test
    void shouldUpdateProduct() throws Exception {
        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productName": "Kindle 2026",
                                  "productUrl": "https://example.com/kindle-2026",
                                  "platform": "amazon",
                                  "currentPrice": 149.99,
                                  "currency": "USD"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(productService).updateProduct(eq(1L), org.mockito.ArgumentMatchers.any(ProductUpdateDto.class));
    }

    @Test
    void shouldDeleteProduct() throws Exception {
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(productService).deleteProduct(1L);
    }
}
