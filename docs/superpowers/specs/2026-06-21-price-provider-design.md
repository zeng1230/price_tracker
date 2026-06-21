# PriceProvider 数据源抽象设计

## 目标与边界

本次在现有单体 Spring Boot 应用内引入可扩展的价格数据源抽象，将 Mock 报价生成从 `PriceServiceImpl` 中移出。只实现接口、标准报价、Mock Provider、稳定路由、服务改造、单元测试和文档说明。

本次不接入 SerpApi，不编写爬虫，不访问真实 Amazon，不修改数据库表结构、RabbitMQ 拓扑、Redis key、HTTP 接口或现有通知流程。

## 组件设计

### PriceProvider

`PriceProvider` 定义三个职责明确的方法：

- `providerCode()` 返回稳定的数据源标识，例如 `MOCK`。
- `supports(Product)` 判断 Provider 是否支持商品。
- `fetchPrice(Product)` 只获取并返回报价，不写数据库、不发送 MQ、不操作 Redis。

### PriceQuote

`PriceQuote` 使用不可变 Java record，字段为 `price`、`currency`、`source`、`capturedAt`、`externalProductId` 和 `productTitle`。

compact constructor 强制：

- `price` 非空且大于等于零。
- `currency` 非空且去除首尾空白后仍有内容。
- `source` 非空且去除首尾空白后仍有内容。
- `capturedAt` 非空。

币种不做隐式默认。Mock Provider 负责从商品币种取值；商品币种为空时明确使用项目默认币种 `USD`。这样报价对象自身始终完整，同时默认规则只存在于数据源适配层。

### MockPriceProvider

`MockPriceProvider` 是 Spring 组件，复用保留的 `PriceMockUtil` 生成随机价格，不访问网络。它支持所有非空商品，以兼容历史数据中不规范的 `platform`。

Provider code 和报价 source 均为 `MOCK`。该实现使用 `@Order(Ordered.LOWEST_PRECEDENCE)`，只作为本地开发、测试和当前默认报价来源的兼容性兜底，不代表真实价格来源。

### PriceProviderRouter

Router 通过构造器注入全部 `PriceProvider`。初始化时先按 `providerCode` 字典序排序，再使用 Spring `AnnotationAwareOrderComparator` 做稳定的 order 排序。因此：

1. 较小 order 的真实 Provider 优先。
2. order 相同时按 `providerCode` 字典序选择。
3. Mock Provider 始终处于最低优先级。

路由时收集所有 `supports(product)` 的 Provider。无候选时抛出 `BusinessException(ResultCode.PRICE_PROVIDER_NOT_FOUND, ...)`，错误信息包含 `productId` 和 `platform`。有多个候选时记录候选 `providerCode` 列表；每次选择均记录最终 `providerCode`、`productId` 和 `platform`。

## PriceServiceImpl 数据流

刷新流程改为：

1. 查询有效商品并确定旧价格。
2. Router 选择 Provider 并返回 `PriceQuote`。
3. 使用 quote 的 `price` 更新当前价格，使用 quote 的 `currency` 更新商品币种，使用 quote 的 `capturedAt` 更新检查时间。
4. 价格变化时，使用 quote 的 `price`、`source`、`capturedAt` 写入价格历史。
5. 使用同一 quote 的价格和采集时间执行现有目标价判断及 RabbitMQ 通知。
6. 保持现有 Redis 缓存删除逻辑不变。

价格未变化时仍只更新商品检查时间和币种、清理现有商品缓存，不写价格历史、不发送通知。`PriceServiceImpl` 不再依赖 `PriceMockUtil`；该工具仅由 `MockPriceProvider` 使用。

## 错误处理

新增 `PRICE_PROVIDER_NOT_FOUND` 业务错误码。Router 不静默回退、不伪造报价。当前 Mock Provider 在正常应用配置中提供兜底；单元测试通过不支持商品的 Provider 集合验证无候选异常。

未来真实 Provider 的超时、限流、无效数据和商品不存在应在适配层分类为明确异常，不能把失败转换成 Mock 报价。

## 测试设计

- `PriceQuoteTest`：验证价格、币种、来源和采集时间的构造约束。
- `MockPriceProviderTest`：验证支持商品、合法报价、默认 USD、`MOCK` source，且复用 `PriceMockUtil`。
- `PriceProviderRouterTest`：验证 Mock 选择、无候选异常信息、较小 order 优先，以及同 order 时按 providerCode 稳定选择。
- `PriceServiceImplTest`：将 `PriceMockUtil` mock 替换为 Router mock，验证 quote 驱动的商品更新、历史写入、MQ 通知、不满足目标价不通知、缓存失效和批量刷新重试行为。

先写失败测试并确认失败原因，再实现最小生产代码，最后运行相关测试、完整测试和编译。

## 文档

README 将明确：默认来源是 `MockPriceProvider`；项目已有 `PriceProvider` 扩展点；Mock 是开发/测试兼容性兜底；当前没有真实 Amazon、SerpApi 或爬虫；未来真实 Provider 位于适配层并通过配置显式启用；外部失败需要分类处理超时、限流、无效数据和商品不存在。
