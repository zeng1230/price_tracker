# Redis Key 与缓存验证

## 1. Key 清单

所有 Redis key 由 `RedisKeyManager` 集中生成，统一前缀为 `price-tracker:`。

| 用途 | 方法 | Pattern | 示例 |
| --- | --- | --- | --- |
| 商品详情缓存 | `productDetailKey(productId)` | `price-tracker:cache:product:detail:{productId}` | `price-tracker:cache:product:detail:10` |
| 商品价格缓存 | `productPriceKey(productId)` | `price-tracker:cache:product:price:{productId}` | `price-tracker:cache:product:price:10` |
| 用户关注列表缓存 | `userWatchlistKey(userId)` | `price-tracker:cache:user:watchlist:{userId}` | `price-tracker:cache:user:watchlist:8` |
| 空值缓存 | `nullValueKey(businessKey)` | `price-tracker:cache:null:{businessKey}` | `price-tracker:cache:null:product:detail:10` |
| 分布式锁 | `lockKey(businessKey)` | `price-tracker:lock:{businessKey}` | `price-tracker:lock:product:price:10` |
| 接口限流 | `rateLimitKey(userId, apiPath)` | `price-tracker:rate-limit:{userId}:{apiPath}` | `price-tracker:rate-limit:3:/api/watchlist` |
| 通知幂等 | `notificationIdempotentKey(businessId)` | `price-tracker:idempotent:notify:{businessId}` | `price-tracker:idempotent:notify:mq:msg-001` |

已有测试覆盖：

- `RedisKeyManagerTest.buildsProductAndUserCacheKeys`
- `RedisKeyManagerTest.buildsInfrastructureKeys`

## 2. TTL 清单

| 区域 | TTL | 代码来源 |
| --- | --- | --- |
| 商品详情缓存 | 基础 30 分钟，加 0-5 分钟随机抖动 | `ProductServiceImpl.CACHE_BASE_TTL`、`CACHE_TTL_JITTER` |
| 商品价格缓存 | 基础 30 分钟，加 0-5 分钟随机抖动 | `ProductServiceImpl.CACHE_BASE_TTL`、`CACHE_TTL_JITTER` |
| 商品空值缓存 | 2 分钟 | `ProductServiceImpl.NULL_CACHE_TTL` |
| 商品详情/价格重建锁 | 10 秒 | `ProductServiceImpl.LOCK_TTL` |
| 关注列表缓存 | 基础 10 分钟，加 0-2 分钟随机抖动 | `WatchlistServiceImpl.WATCHLIST_CACHE_BASE_TTL`、`WATCHLIST_CACHE_TTL_JITTER` |
| 价格刷新侧通知幂等 | 默认 10 分钟 | `notification.idempotent.ttl-minutes` |
| MQ 消费侧幂等 | 默认 30 分钟 | `notification.consumer-idempotent.ttl-minutes` |
| 接口限流窗口 | 注解值或默认 60 秒 | `@RateLimit`、`RedisRateLimitProperties.defaultWindowSeconds` |

`RedisCacheService.randomTtl(...)` 要求基础 TTL 为正数，并在基础 TTL 上增加随机抖动，用于降低热点 key 同时过期的风险。

已有测试覆盖：

- `RedisCacheServiceTest.setIfAbsentUsesRedisTtl`
- `RedisCacheServiceTest.randomTtlAddsBoundedJitter`

## 3. 缓存行为

商品详情与当前价格：

- 查询时优先读取 Redis。
- 命中缓存时直接返回缓存 VO，并记录 `cache hit`。
- 未命中缓存时先检查空值缓存。
- 命中空值缓存时直接返回 not found，不回源数据库。
- 回源数据库前使用 Redis 分布式锁，降低缓存击穿风险。
- 商品更新、删除、价格刷新后清理商品详情缓存、商品价格缓存，以及对应空值缓存。

关注列表：

- `pageMyWatchlist(...)` 读取 `price-tracker:cache:user:watchlist:{userId}`。
- 关注新增、更新、删除后清理当前用户关注列表缓存。

已有测试覆盖：

- `ProductServiceImplTest.shouldReturnCachedProductDetailBeforeDatabaseLookup`
- `ProductServiceImplTest.shouldLoadProductDetailFromDatabaseAndCacheWhenRedisMisses`
- `ProductServiceImplTest.shouldCacheNullWhenProductDetailDoesNotExist`
- `ProductServiceImplTest.shouldNotQueryDatabaseWhenNullProductDetailCacheHits`
- `ProductServiceImplTest.shouldReturnCachedCurrentPriceBeforeDatabaseLookup`
- `ProductServiceImplTest.shouldCacheCurrentPriceAfterDatabaseFallback`
- `WatchlistServiceImplTest.pageMyWatchlistReturnsCachedResultBeforeDatabaseLookup`

## 4. 分布式锁行为

`RedisDistributedLock` 使用 Redis `setIfAbsent(key, owner, ttl)` 获取锁。

规则：

- TTL 为 null、0 或负数时不会获取锁。
- 解锁使用 Lua 脚本，仅当 Redis 中保存的 owner 与调用方 owner 一致时才删除 key。
- 商品详情和商品价格缓存重建使用不同锁：
  - `price-tracker:lock:product:detail:{productId}`
  - `price-tracker:lock:product:price:{productId}`

已有测试覆盖：

- `RedisDistributedLockTest.tryLockRequiresTtlAndUsesSetIfAbsent`
- `RedisDistributedLockTest.unlockUsesOwnerCheckScript`

## 5. 限流行为

限流使用 `@RateLimit` 与 `RedisRateLimitAspect` 实现。当前代码只在关注相关写接口上使用该注解：

- `POST /api/watchlist`
- `PUT /api/watchlist/{id}`
- `DELETE /api/watchlist/{id}`

流程：

1. Aspect 从 `UserContext` 获取当前用户 ID。
2. Aspect 从 `RequestContextHolder` 获取请求路径。
3. 根据注解配置或默认配置计算 limit 与 window。
4. `RedisRateLimiter` 递增 `price-tracker:rate-limit:{userId}:{apiPath}`。
5. 当计数为 `1` 时设置过期时间。
6. 超过限制时抛出 `BusinessException(TOO_MANY_REQUESTS)`。

已有测试覆盖：

- `RedisRateLimitAspectTest.blocksWhenRateLimitThresholdIsExceeded`

## 6. 幂等行为

价格刷新侧通知入口幂等：

- 位置：`PriceServiceImpl.sendAlertIfNotDuplicate(...)`
- Key：`price-tracker:idempotent:notify:{userId}:{productId}:{targetPrice}`
- TTL：`notification.idempotent.ttl-minutes`，默认 10 分钟
- 目的：避免同一用户、商品、目标价在短时间重复刷新时重复发送 MQ 消息。

MQ 消费侧幂等：

- 位置：`PriceAlertConsumer.consumeInternal(...)`
- 优先 key 来源：`messageId`
- 优先 key pattern：`price-tracker:idempotent:notify:mq:{messageId}`
- fallback 业务字段：`userId`、`productId`、`targetPrice`、`currentPrice`、`triggeredAt`
- TTL：`notification.consumer-idempotent.ttl-minutes`，默认 30 分钟
- 重复行为：记录 `decision=ack_skip` 并返回，不调用通知服务。
- 失败行为：记录 `decision=ack_keep_idempotent_key_until_ttl`，不重新抛出异常。

业务落库幂等：

- 位置：`NotificationServiceImpl.consumePriceAlert(...)`
- 规则：如果 `tb_watchlist.last_notified_price == currentPrice`，跳过通知插入。

已有测试覆盖：

- `PriceServiceImplTest.refreshProductPriceSkipsDuplicateAlertWhenIdempotentKeyAlreadyExists`
- `PriceAlertConsumerTest.consumeSkipsDuplicateMessageWhenIdempotentKeyAlreadyExists`
- `PriceAlertConsumerTest.consumeAcksBySkippingWhenIdempotentKeyIsHit`
- `NotificationServiceImplTest.consumePriceAlertSkipsDuplicatePriceNotification`

## 7. 关键日志示例

Producer：

```text
Publishing price alert message, messageId=7-6-f211ef7c-6996-496e-8e9f-d229805c084e, exchange=price.alert.exchange, routingKey=price.alert, watchlistId=6, productId=7, userId=7, productName=Laptop, currentPrice=79.00, targetPrice=80.00
Published price alert message successfully, messageId=7-6-f211ef7c-6996-496e-8e9f-d229805c084e, routingKey=price.alert, watchlistId=6, productId=7, userId=7, productName=Laptop, currentPrice=79.00
```

Consumer：

```text
Received price alert message from queue=price.alert.queue, messageId=msg-001, watchlistId=5, productId=1, userId=99, currentPrice=79.00, targetPrice=80.00
Notification send success, key=price-tracker:idempotent:notify:mq:msg-001, messageId=msg-001, watchlistId=5, productId=1, userId=99
Idempotent hit for price alert message, key=price-tracker:idempotent:notify:mq:msg-001, messageId=msg-001, watchlistId=5, productId=1, userId=99, decision=ack_skip
Notification send failed, key=price-tracker:idempotent:notify:mq:msg-001, messageId=msg-001, watchlistId=5, productId=1, userId=99, decision=ack_keep_idempotent_key_until_ttl
```

缓存与限流：

```text
cache hit, key=price-tracker:cache:product:detail:1
cache miss, key=price-tracker:cache:product:price:7
lock acquired, key=price-tracker:lock:product:price:7
rate limited, userId=7, apiPath=/api/watchlist, limit=5, windowSeconds=60
notification idempotent hit, key=price-tracker:idempotent:notify:99:1:80.00
```

MQ 日志包含必要业务标识：`userId`、`productId`、`watchlistId`、`messageId`。

## 8. 已知限制

- Redis 幂等 TTL 是时间窗口控制，不是永久去重记录。
- 价格刷新侧幂等当前使用 `userId:productId:targetPrice`，因此同一目标价下的不同触发价格会共享短时间幂等窗口。
- consumer 失败时会保留幂等 key 到 TTL 过期，能避免重试风暴，但也会在 TTL 内抑制失败通知的再次处理。
- 本文档不覆盖 Redis Cluster、Sentinel 或故障转移。
- 限流使用固定窗口计数，不是滑动窗口或令牌桶。
- 缓存一致性基于失效策略，没有实现 write-through 缓存。
