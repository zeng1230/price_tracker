# PriceProvider Abstraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decouple price acquisition from `PriceServiceImpl` through a validated quote contract, ordered Provider routing, a Mock fallback, and regression tests.

**Architecture:** `PriceProvider` implementations return immutable `PriceQuote` values and have no persistence, MQ, or Redis side effects. `PriceProviderRouter` selects a supporting Provider using one comparator ordered by resolved Spring order and then provider code; `PriceServiceImpl` consumes the quote while preserving its existing database, RabbitMQ, and Redis flow.

**Tech Stack:** Java 17 records, Spring Boot 3.3.4, Spring Core ordering/AOP utilities, JUnit 5, Mockito, MyBatis-Plus.

---

### Task 1: Validated PriceQuote contract

**Files:**
- Create: `src/main/java/com/example/price_tracker/provider/PriceQuote.java`
- Create: `src/test/java/com/example/price_tracker/provider/PriceQuoteTest.java`

- [ ] **Step 1: Write failing constructor-validation tests**

Create tests that construct a complete quote and assert `IllegalArgumentException` for null/negative price, blank currency/source, and null `capturedAt`:

```java
PriceQuote quote = new PriceQuote(new BigDecimal("99.00"), "USD", "MOCK", capturedAt, null, "Laptop");
assertEquals(new BigDecimal("99.00"), quote.price());
assertThrows(IllegalArgumentException.class,
        () -> new PriceQuote(BigDecimal.ONE.negate(), "USD", "MOCK", capturedAt, null, null));
```

- [ ] **Step 2: Run the test and verify RED**

Run: `.\mvnw.cmd -q -Dtest=PriceQuoteTest test`

Expected: compilation fails because `PriceQuote` does not exist.

- [ ] **Step 3: Implement the immutable record**

```java
public record PriceQuote(BigDecimal price, String currency, String source,
                         LocalDateTime capturedAt, String externalProductId,
                         String productTitle) {
    public PriceQuote {
        if (price == null) throw new IllegalArgumentException("price must not be null");
        if (price.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("price must be greater than or equal to zero");
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency must not be blank");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("source must not be blank");
        if (capturedAt == null) throw new IllegalArgumentException("capturedAt must not be null");
        currency = currency.trim();
        source = source.trim();
    }
}
```

- [ ] **Step 4: Run the test and verify GREEN**

Run: `.\mvnw.cmd -q -Dtest=PriceQuoteTest test`

Expected: PASS.

### Task 2: PriceProvider and Mock fallback

**Files:**
- Create: `src/main/java/com/example/price_tracker/provider/PriceProvider.java`
- Create: `src/main/java/com/example/price_tracker/provider/impl/MockPriceProvider.java`
- Create: `src/test/java/com/example/price_tracker/provider/impl/MockPriceProviderTest.java`
- Preserve: `src/main/java/com/example/price_tracker/util/PriceMockUtil.java`

- [ ] **Step 1: Write failing Mock Provider tests**

Use a mocked `PriceMockUtil` to make the quote deterministic. Verify all non-null products are supported, `MOCK` is used for code/source, product currency is preserved, blank currency defaults to `USD`, and title is copied.

```java
when(priceMockUtil.generateNextPrice(product.getCurrentPrice())).thenReturn(new BigDecimal("91.50"));
PriceQuote quote = provider.fetchPrice(product);
assertEquals("MOCK", provider.providerCode());
assertEquals("CNY", quote.currency());
assertEquals("MOCK", quote.source());
assertNotNull(quote.capturedAt());
```

- [ ] **Step 2: Run the test and verify RED**

Run: `.\mvnw.cmd -q -Dtest=MockPriceProviderTest test`

Expected: compilation fails because Provider types do not exist.

- [ ] **Step 3: Implement interface and lowest-priority Mock Provider**

```java
public interface PriceProvider {
    String providerCode();
    boolean supports(Product product);
    PriceQuote fetchPrice(Product product);
}
```

```java
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class MockPriceProvider implements PriceProvider {
    private static final String PROVIDER_CODE = "MOCK";
    private static final String DEFAULT_CURRENCY = "USD";
    private final PriceMockUtil priceMockUtil;

    public String providerCode() { return PROVIDER_CODE; }
    public boolean supports(Product product) { return product != null; }
    public PriceQuote fetchPrice(Product product) {
        BigDecimal price = priceMockUtil.generateNextPrice(product.getCurrentPrice());
        String currency = product.getCurrency() == null || product.getCurrency().isBlank()
                ? DEFAULT_CURRENCY : product.getCurrency();
        return new PriceQuote(price, currency, PROVIDER_CODE, LocalDateTime.now(), null, product.getProductName());
    }
}
```

- [ ] **Step 4: Run the tests and verify GREEN**

Run: `.\mvnw.cmd -q -Dtest=PriceQuoteTest,MockPriceProviderTest test`

Expected: PASS.

### Task 3: Stable and defensive Provider routing

**Files:**
- Create: `src/main/java/com/example/price_tracker/provider/PriceProviderRouter.java`
- Create: `src/test/java/com/example/price_tracker/provider/PriceProviderRouterTest.java`
- Modify: `src/main/java/com/example/price_tracker/common/ResultCode.java`

- [ ] **Step 1: Write failing Router tests**

Cover Mock selection, no-candidate exception containing product id/platform, `Ordered` priority, target-class `@Order` priority, provider-code tie breaking, and a throwing `supports` implementation being skipped.

```java
PriceProviderRouter router = new PriceProviderRouter(List.of(zuluProvider, alphaProvider));
assertSame(alphaProvider, router.route(product));

BusinessException exception = assertThrows(BusinessException.class, () -> router.route(product));
assertEquals(ResultCode.PRICE_PROVIDER_NOT_FOUND.getCode(), exception.getCode());
assertTrue(exception.getMessage().contains("productId=7"));
assertTrue(exception.getMessage().contains("platform=UNKNOWN"));
```

- [ ] **Step 2: Run the test and verify RED**

Run: `.\mvnw.cmd -q -Dtest=PriceProviderRouterTest test`

Expected: compilation fails because Router and error code do not exist.

- [ ] **Step 3: Add the business error code**

Add `PRICE_PROVIDER_NOT_FOUND(1001, "price provider not found")` to `ResultCode`.

- [ ] **Step 4: Implement one explicit comparator and defensive supports evaluation**

```java
private int resolveOrder(PriceProvider provider) {
    if (provider instanceof Ordered ordered) return ordered.getOrder();
    Class<?> targetClass = AopUtils.getTargetClass(provider);
    return OrderUtils.getOrder(targetClass, Ordered.LOWEST_PRECEDENCE);
}

private Comparator<PriceProvider> providerComparator() {
    return Comparator.comparingInt(this::resolveOrder)
            .thenComparing(PriceProvider::providerCode);
}
```

Copy and sort the injected list once. In `route`, catch `RuntimeException` from each `supports`, log provider/product/platform/error summary, skip it, log multiple candidate codes and the final selected code, then throw `PRICE_PROVIDER_NOT_FOUND` with product id and platform if empty.

- [ ] **Step 5: Run Router tests and verify GREEN**

Run: `.\mvnw.cmd -q -Dtest=PriceProviderRouterTest test`

Expected: PASS.

### Task 4: Refactor PriceServiceImpl without changing side-effect chains

**Files:**
- Modify: `src/main/java/com/example/price_tracker/service/impl/PriceServiceImpl.java`
- Modify: `src/test/java/com/example/price_tracker/service/impl/PriceServiceImplTest.java`

- [ ] **Step 1: Change service tests to the desired Router contract**

Replace the `PriceMockUtil` mock with `PriceProviderRouter` and a mocked `PriceProvider`. Return deterministic quotes and capture the stored entities.

```java
when(priceProviderRouter.route(any(Product.class))).thenReturn(priceProvider);
when(priceProvider.fetchPrice(any(Product.class))).thenReturn(
        new PriceQuote(new BigDecimal("79.00"), "CNY", "MOCK", CAPTURED_AT, null, "Laptop"));
```

Assert current price/currency/last-checked time, history price/source/captured time, existing MQ behavior, no alert above target, and unchanged Redis key deletions. Update paging/retry fixtures to return deterministic quotes.

- [ ] **Step 2: Run service tests and verify RED**

Run: `.\mvnw.cmd -q -Dtest=PriceServiceImplTest test`

Expected: failures because the service still depends on `PriceMockUtil` and does not use quote metadata.

- [ ] **Step 3: Refactor the service minimally**

Replace `PriceMockUtil` with `PriceProviderRouter`. Route and fetch once per attempt:

```java
PriceQuote quote = priceProviderRouter.route(product).fetchPrice(product);
BigDecimal newPrice = quote.price();
LocalDateTime capturedAt = quote.capturedAt();
product.setCurrency(quote.currency());
product.setLastCheckedAt(capturedAt);
```

Use `quote.source()` and `capturedAt` for history; use `capturedAt` for notification trigger time. Preserve update, history-change condition, watchlist query, producer call, retries, and all cache deletions.

- [ ] **Step 4: Run service and Provider tests and verify GREEN**

Run: `.\mvnw.cmd -q -Dtest=PriceQuoteTest,MockPriceProviderTest,PriceProviderRouterTest,PriceServiceImplTest test`

Expected: PASS.

### Task 5: Document current and future data-source boundaries

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update current-source documentation**

State that `MockPriceProvider` is the current default and lowest-priority development/test compatibility fallback, not a real source. Document the `PriceProvider` extension point, absence of real Amazon/SerpApi/crawlers, explicit configuration for future adapters, and timeout/rate-limit/invalid-data/product-not-found failure classification.

- [ ] **Step 2: Check documentation consistency**

Run: `rg -n "PriceMockUtil Mock 数据源|PriceProvider.*未实现|source=mock" README.md`

Expected: no stale capability statements; database history documentation uses `source=MOCK`.

### Task 6: Final verification

**Files:**
- Verify all files above without modifying unrelated worktree changes.

- [ ] **Step 1: Run required focused tests**

Run: `.\mvnw.cmd -q -Dtest=PriceQuoteTest,MockPriceProviderTest,PriceProviderRouterTest,PriceServiceImplTest test`

Expected: PASS with zero failures/errors.

- [ ] **Step 2: Run full test suite**

Run: `.\mvnw.cmd -q test`

Expected: PASS with zero failures/errors.

- [ ] **Step 3: Run compilation**

Run: `.\mvnw.cmd -q -DskipTests compile`

Expected: exit code 0.

- [ ] **Step 4: Inspect scope and whitespace**

Run: `git diff --check` and `git status --short`.

Expected: no whitespace errors; only intended new/modified files plus pre-existing user changes.
