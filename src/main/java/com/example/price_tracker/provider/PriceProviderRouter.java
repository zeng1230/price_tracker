package com.example.price_tracker.provider;

import com.example.price_tracker.common.ResultCode;
import com.example.price_tracker.entity.Product;
import com.example.price_tracker.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@Slf4j
public class PriceProviderRouter {

    private final List<PriceProvider> providers;

    public PriceProviderRouter(List<PriceProvider> providers) {
        this.providers = providers.stream()
                .sorted(providerComparator())
                .toList();
    }

    public PriceProvider route(Product product) {
        Long productId = product == null ? null : product.getId();
        String platform = product == null ? null : product.getPlatform();
        List<PriceProvider> candidates = providers.stream()
                .filter(provider -> supportsSafely(provider, product, productId, platform))
                .toList();

        if (candidates.isEmpty()) {
            throw new BusinessException(
                    ResultCode.PRICE_PROVIDER_NOT_FOUND,
                    "price provider not found, productId=" + productId + ", platform=" + platform);
        }

        if (candidates.size() > 1) {
            log.info("multiple price providers support product, productId={}, platform={}, candidates={}",
                    productId,
                    platform,
                    candidates.stream().map(PriceProvider::providerCode).toList());
        }

        PriceProvider selected = candidates.get(0);
        log.info("price provider selected, providerCode={}, productId={}, platform={}",
                selected.providerCode(), productId, platform);
        return selected;
    }

    private boolean supportsSafely(
            PriceProvider provider,
            Product product,
            Long productId,
            String platform) {
        try {
            return provider.supports(product);
        } catch (RuntimeException exception) {
            log.warn("price provider supports failed, providerCode={}, productId={}, platform={}, error={}",
                    provider.providerCode(), productId, platform, exception.toString());
            return false;
        }
    }

    private Comparator<PriceProvider> providerComparator() {
        return Comparator.comparingInt(this::resolveOrder)
                .thenComparing(PriceProvider::providerCode);
    }

    private int resolveOrder(PriceProvider provider) {
        if (provider instanceof Ordered ordered) {
            return ordered.getOrder();
        }
        Class<?> targetClass = AopUtils.getTargetClass(provider);
        return OrderUtils.getOrder(targetClass, Ordered.LOWEST_PRECEDENCE);
    }
}
