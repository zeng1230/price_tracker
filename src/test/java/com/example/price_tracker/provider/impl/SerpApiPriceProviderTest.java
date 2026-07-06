package com.example.price_tracker.provider.impl;

import com.example.price_tracker.entity.Product;
import com.example.price_tracker.provider.PriceProviderException;
import com.example.price_tracker.provider.PriceProviderFailureType;
import com.example.price_tracker.provider.PriceQuote;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SerpApiPriceProviderTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void disabledProviderDoesNotSupportProducts() {
        SerpApiPriceProvider provider = provider("http://127.0.0.1:1/search", false);

        assertThat(provider.supports(product())).isFalse();
    }

    @Test
    void fetchPriceParsesFirstShoppingResult() throws Exception {
        server = startServer(200, """
                {
                  "shopping_results": [
                    {
                      "title": "Laptop Pro",
                      "product_id": "B001",
                      "extracted_price": 799.99,
                      "currency": "USD"
                    }
                  ]
                }
                """);
        SerpApiPriceProvider provider = provider("http://127.0.0.1:" + server.getAddress().getPort() + "/search", true);

        PriceQuote quote = provider.fetchPrice(product());

        assertThat(provider.providerCode()).isEqualTo("SERPAPI");
        assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("799.99"));
        assertThat(quote.currency()).isEqualTo("USD");
        assertThat(quote.source()).isEqualTo("SERPAPI");
        assertThat(quote.externalProductId()).isEqualTo("B001");
        assertThat(quote.productTitle()).isEqualTo("Laptop Pro");
    }

    @Test
    void fetchPriceClassifiesRateLimitAsRetryable() throws Exception {
        server = startServer(429, "{\"error\":\"rate limit\"}");
        SerpApiPriceProvider provider = provider("http://127.0.0.1:" + server.getAddress().getPort() + "/search", true);

        assertThatThrownBy(() -> provider.fetchPrice(product()))
                .isInstanceOfSatisfying(PriceProviderException.class, exception -> {
                    assertThat(exception.getFailureType()).isEqualTo(PriceProviderFailureType.RATE_LIMITED);
                    assertThat(exception.isRetryable()).isTrue();
                });
    }

    private HttpServer startServer(int status, String body) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/search", exchange -> {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        httpServer.start();
        return httpServer;
    }

    private SerpApiPriceProvider provider(String baseUrl, boolean enabled) {
        SerpApiPriceProvider provider = new SerpApiPriceProvider();
        ReflectionTestUtils.setField(provider, "enabled", enabled);
        ReflectionTestUtils.setField(provider, "apiKey", "test-key");
        ReflectionTestUtils.setField(provider, "baseUrl", baseUrl);
        ReflectionTestUtils.setField(provider, "timeoutMillis", 1000L);
        return provider;
    }

    private Product product() {
        return Product.builder()
                .id(1L)
                .productName("Laptop")
                .productUrl("https://example.com/laptop")
                .platform("amazon")
                .currency("USD")
                .currentPrice(new BigDecimal("900.00"))
                .build();
    }
}
