package com.example.price_tracker.provider.impl;

import com.example.price_tracker.entity.Product;
import com.example.price_tracker.provider.PriceProvider;
import com.example.price_tracker.provider.PriceProviderException;
import com.example.price_tracker.provider.PriceProviderFailureType;
import com.example.price_tracker.provider.PriceQuote;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SerpApiPriceProvider implements PriceProvider {

    private static final String PROVIDER_CODE = "SERPAPI";
    private static final String SUPPORTED_PLATFORM = "amazon";
    private static final String DEFAULT_CURRENCY = "USD";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${price-provider.serpapi.enabled:false}")
    private boolean enabled = false;

    @Value("${price-provider.serpapi.api-key:}")
    private String apiKey;

    @Value("${price-provider.serpapi.base-url:https://serpapi.com/search.json}")
    private String baseUrl;

    @Value("${price-provider.serpapi.timeout-ms:3000}")
    private long timeoutMillis = 3000;

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public boolean supports(Product product) {
        return enabled
                && product != null
                && product.getPlatform() != null
                && SUPPORTED_PLATFORM.equalsIgnoreCase(product.getPlatform());
    }

    @Override
    public PriceQuote fetchPrice(Product product) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new PriceProviderException(PriceProviderFailureType.AUTHENTICATION_FAILED, false, "serpapi api key is blank");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(buildUri(product))
                    .timeout(Duration.ofMillis(resolveTimeoutMillis()))
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            return parseResponse(response.statusCode(), response.body(), product);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PriceProviderException(PriceProviderFailureType.TIMEOUT, true, "serpapi request interrupted", exception);
        } catch (PriceProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PriceProviderException(PriceProviderFailureType.UPSTREAM_ERROR, true, "serpapi request failed: " + exception.getMessage(), exception);
        }
    }

    private URI buildUri(Product product) {
        String query = product.getProductUrl() == null || product.getProductUrl().isBlank()
                ? product.getProductName()
                : product.getProductUrl();
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("engine", "amazon")
                .queryParam("api_key", apiKey)
                .queryParam("q", query)
                .build(true)
                .toUri();
    }

    private PriceQuote parseResponse(int status, String body, Product product) throws Exception {
        if (status == 401 || status == 403) {
            throw new PriceProviderException(PriceProviderFailureType.AUTHENTICATION_FAILED, false, "serpapi authentication failed status=" + status);
        }
        if (status == 429) {
            throw new PriceProviderException(PriceProviderFailureType.RATE_LIMITED, true, "serpapi rate limited status=429");
        }
        if (status >= 500) {
            throw new PriceProviderException(PriceProviderFailureType.UPSTREAM_ERROR, true, "serpapi upstream error status=" + status);
        }
        if (status < 200 || status >= 300) {
            throw new PriceProviderException(PriceProviderFailureType.INVALID_RESPONSE, false, "serpapi unexpected status=" + status);
        }

        JsonNode root = objectMapper.readTree(body);
        JsonNode results = root.path("shopping_results");
        if (!results.isArray() || results.isEmpty()) {
            throw new PriceProviderException(PriceProviderFailureType.PRODUCT_NOT_FOUND, false, "serpapi product not found");
        }
        JsonNode first = results.get(0);
        JsonNode priceNode = first.path("extracted_price");
        if (!priceNode.isNumber()) {
            throw new PriceProviderException(PriceProviderFailureType.INVALID_RESPONSE, false, "serpapi missing extracted_price");
        }
        String currency = textOrDefault(first.path("currency"), product.getCurrency(), DEFAULT_CURRENCY);
        String title = textOrDefault(first.path("title"), product.getProductName(), null);
        String externalProductId = first.path("product_id").asText(null);
        return new PriceQuote(
                priceNode.decimalValue(),
                currency,
                PROVIDER_CODE,
                LocalDateTime.now(),
                externalProductId,
                title);
    }

    private long resolveTimeoutMillis() {
        return timeoutMillis > 0 ? timeoutMillis : 3000;
    }

    private String textOrDefault(JsonNode node, String firstDefault, String secondDefault) {
        if (node != null && node.isTextual() && !node.asText().isBlank()) {
            return node.asText();
        }
        if (firstDefault != null && !firstDefault.isBlank()) {
            return firstDefault;
        }
        return secondDefault;
    }
}
