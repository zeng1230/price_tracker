package com.example.price_tracker.provider;

import com.example.price_tracker.common.ResultCode;
import com.example.price_tracker.entity.Product;
import com.example.price_tracker.exception.BusinessException;
import com.example.price_tracker.provider.impl.MockPriceProvider;
import com.example.price_tracker.util.PriceMockUtil;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PriceProviderRouterTest {

    @Test
    void selectsMockPriceProviderWhenItIsTheOnlyCandidate() {
        MockPriceProvider mockProvider = new MockPriceProvider(mock(PriceMockUtil.class));
        PriceProviderRouter router = new PriceProviderRouter(List.of(mockProvider));

        assertSame(mockProvider, router.route(product()));
    }

    @Test
    void throwsBusinessExceptionWithProductContextWhenNoProviderSupportsProduct() {
        PriceProviderRouter router = new PriceProviderRouter(List.of(
                new StubProvider("UNSUPPORTED", false)));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> router.route(product()));

        assertEquals(ResultCode.PRICE_PROVIDER_NOT_FOUND.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("productId=7"));
        assertTrue(exception.getMessage().contains("platform=UNKNOWN"));
    }

    @Test
    void selectsLowerOrderedValueBeforeFallback() {
        PriceProvider fallback = new OrderedProvider("MOCK", true, Ordered.LOWEST_PRECEDENCE);
        PriceProvider real = new OrderedProvider("REAL", true, 10);
        PriceProviderRouter router = new PriceProviderRouter(List.of(fallback, real));

        assertSame(real, router.route(product()));
    }

    @Test
    void readsOrderAnnotationFromProxiedTargetClass() {
        PriceProvider annotatedTarget = new AnnotatedProvider();
        ProxyFactory proxyFactory = new ProxyFactory(annotatedTarget);
        proxyFactory.setProxyTargetClass(true);
        PriceProvider proxiedProvider = (PriceProvider) proxyFactory.getProxy();
        PriceProvider fallback = new OrderedProvider("MOCK", true, Ordered.LOWEST_PRECEDENCE);
        PriceProviderRouter router = new PriceProviderRouter(List.of(fallback, proxiedProvider));

        assertSame(proxiedProvider, router.route(product()));
    }

    @Test
    void selectsProviderCodeAlphabeticallyWhenOrdersAreEqual() {
        PriceProvider zulu = new StubProvider("ZULU", true);
        PriceProvider alpha = new StubProvider("ALPHA", true);
        PriceProviderRouter router = new PriceProviderRouter(List.of(zulu, alpha));

        assertSame(alpha, router.route(product()));
    }

    @Test
    void skipsProviderWhenSupportsThrows() {
        PriceProvider failing = new ThrowingSupportsProvider("BROKEN");
        PriceProvider fallback = new StubProvider("MOCK", true);
        PriceProviderRouter router = new PriceProviderRouter(List.of(failing, fallback));

        assertSame(fallback, router.route(product()));
    }

    private Product product() {
        Product product = new Product();
        product.setId(7L);
        product.setPlatform("UNKNOWN");
        return product;
    }

    private static class StubProvider implements PriceProvider {

        private final String providerCode;
        private final boolean supported;

        private StubProvider(String providerCode, boolean supported) {
            this.providerCode = providerCode;
            this.supported = supported;
        }

        @Override
        public String providerCode() {
            return providerCode;
        }

        @Override
        public boolean supports(Product product) {
            return supported;
        }

        @Override
        public PriceQuote fetchPrice(Product product) {
            throw new UnsupportedOperationException("not needed for router selection test");
        }
    }

    private static class OrderedProvider extends StubProvider implements Ordered {

        private final int order;

        private OrderedProvider(String providerCode, boolean supported, int order) {
            super(providerCode, supported);
            this.order = order;
        }

        @Override
        public int getOrder() {
            return order;
        }
    }

    @Order(5)
    static class AnnotatedProvider extends StubProvider {

        public AnnotatedProvider() {
            super("ANNOTATED", true);
        }
    }

    private static class ThrowingSupportsProvider extends StubProvider {

        private ThrowingSupportsProvider(String providerCode) {
            super(providerCode, false);
        }

        @Override
        public boolean supports(Product product) {
            throw new IllegalStateException("supports failed");
        }
    }
}
