# Redis 验收

本文以 `RedisKeyManager` 和当前业务代码为准。命令默认 Redis 容器名为 `price-tracker-redis`，`${productId}`、`${userId}` 等占位符需要替换为实际数字。

## Key 总览

| 类别 | Key 模式 | 默认 TTL | 触发方式 |
| --- | --- | --- | --- |
| 商品详情 | `price-tracker:cache:product:detail:{productId}` | 30～35 分钟 | 首次查询有效商品详情 |
| 商品价格 | `price-tracker:cache:product:price:{productId}` | 30～35 分钟 | 首次查询有效商品价格 |
| 关注列表 | `price-tracker:cache:user:watchlist:{userId}` | 10～12 分钟 | 首次查询当前用户关注列表 |
| 详情空值 | `price-tracker:cache:null:product:detail:{productId}` | 2 分钟 | 查询不存在或无效商品详情 |
| 价格空值 | `price-tracker:cache:null:product:price:{productId}` | 2 分钟 | 查询不存在或无效商品价格 |
| 分布式锁 | `price-tracker:lock:product:{detail|price}:{productId}` | 10 秒以内 | 对应缓存 miss 后重建缓存 |
| 限流 | `price-tracker:rate-limit:{userId}:{apiPath}` | 默认 60 秒 | 调用带 `@RateLimit` 的关注新增、更新或删除接口 |
| Producer 幂等 | `price-tracker:idempotent:notify:{userId}:{productId}:{targetPrice}` | 默认 10 分钟 | 刷新价格且命中关注目标价 |
| Consumer 幂等 | `price-tracker:idempotent:notify:mq:{messageId}` | 默认 30 分钟 | Consumer 收到带 `messageId` 的价格提醒 |

注意：Producer 和 Consumer 共用 `price-tracker:idempotent:notify:` 前缀，区别在后缀是否以 `mq:` 开始；代码没有独立的 `consumerIdempotentKey` 方法。

## 通用检查命令

```powershell
docker exec price-tracker-redis redis-cli ping
docker exec price-tracker-redis redis-cli --scan --pattern "price-tracker:*"
docker exec price-tracker-redis redis-cli TYPE "<key>"
docker exec price-tracker-redis redis-cli TTL "<key>"
docker exec price-tracker-redis redis-cli GET "<key>"
```

预期 `PING` 返回 `PONG`。业务值使用 JSON 序列化，重点检查 key 是否存在、TTL 是否在范围内，不依赖 JSON 字段顺序。

## 1. 商品详情缓存

触发：携带 JWT 调用两次 `GET /api/products/{productId}`。

```powershell
Invoke-RestMethod "$base/api/products/$productId" -Headers $headers | Out-Null
Invoke-RestMethod "$base/api/products/$productId" -Headers $headers | Out-Null
docker exec price-tracker-redis redis-cli EXISTS "price-tracker:cache:product:detail:$productId"
docker exec price-tracker-redis redis-cli TTL "price-tracker:cache:product:detail:$productId"
```

预期：`EXISTS=1`，TTL 初始在 1800～2100 秒之间；日志先出现 `cache miss`/`db fallback`，随后出现 `cache hit`。更新、删除商品或刷新价格会删除详情与价格缓存。Redis 不可用时当前没有数据库直读降级，接口会失败。

## 2. 商品价格缓存

触发：调用两次 `GET /api/products/{productId}/price`。

```powershell
Invoke-RestMethod "$base/api/products/$productId/price" -Headers $headers | Out-Null
docker exec price-tracker-redis redis-cli EXISTS "price-tracker:cache:product:price:$productId"
docker exec price-tracker-redis redis-cli TTL "price-tracker:cache:product:price:$productId"
```

预期：`EXISTS=1`，TTL 初始在 1800～2100 秒之间。手动或定时价格刷新后该 key 被删除，下一次查询回源并重建。删除失败可能短暂返回旧值，直到 TTL 到期或后续删除成功。

## 3. 用户关注列表缓存

触发：调用 `GET /api/watchlist/my?pageNum=1&pageSize=10`。

```powershell
Invoke-RestMethod "$base/api/watchlist/my?pageNum=1&pageSize=10" -Headers $headers | Out-Null
docker exec price-tracker-redis redis-cli EXISTS "price-tracker:cache:user:watchlist:$($register.data.id)"
docker exec price-tracker-redis redis-cli TTL "price-tracker:cache:user:watchlist:$($register.data.id)"
```

预期：`EXISTS=1`，TTL 初始在 600～720 秒之间。新增、更新、取消关注会删除整个用户关注列表缓存。当前 key 不包含分页参数，因此不同 `pageNum/pageSize` 共用同一个缓存结果，这是现有实现边界；验收固定使用默认第一页。

## 4. 空值缓存

选择一个数据库中不存在的 ID，例如 `999999999`：

```powershell
try { Invoke-RestMethod "$base/api/products/999999999" -Headers $headers } catch { $_.ErrorDetails.Message }
docker exec price-tracker-redis redis-cli GET "price-tracker:cache:null:product:detail:999999999"
docker exec price-tracker-redis redis-cli TTL "price-tracker:cache:null:product:detail:999999999"
```

价格空值同理，调用 `/api/products/999999999/price` 并检查 `price-tracker:cache:null:product:price:999999999`。预期接口返回商品不存在，Redis 值表示 `NULL`，TTL 初始不超过 120 秒。第二次查询应命中空值缓存而不回源。空值缓存误命中时会在 TTL 内继续返回不存在；商品创建不复用指定 ID，因此当前不会主动清理任意历史空值 key。

## 5. 分布式锁

先删除详情缓存，再并发请求：

```powershell
docker exec price-tracker-redis redis-cli DEL "price-tracker:cache:product:detail:$productId"
1..20 | ForEach-Object -Parallel {
  Invoke-RestMethod "$using:base/api/products/$using:productId" -Headers $using:headers | Out-Null
} -ThrottleLimit 20
docker exec price-tracker-redis redis-cli EXISTS "price-tracker:lock:product:detail:$productId"
```

预期日志只有持锁请求执行主要回源，其他请求在短暂等待后读缓存；请求结束后锁 key 应为 `0`。锁只存活 10 秒且 finally 中使用 Lua 校验 owner 后释放，运行后扫描不到属于正常结果。竞争请求等待 50ms 后仍无缓存会返回“cache is rebuilding”；锁过期但原线程仍执行时可能出现重复回源，这是短租约锁的已知语义。

## 6. 限流 key

`POST /api/watchlist`、`PUT /api/watchlist/{id}`、`DELETE /api/watchlist/{id}` 使用 `@RateLimit`，默认每用户、每 URI 60 秒 60 次。先调用一次关注更新，再检查：

```powershell
Invoke-RestMethod -Method Put -Uri "$base/api/watchlist/$watchlistId" -Headers $headers `
  -ContentType "application/json" -Body (@{ targetPrice=999999.00; notifyEnabled=1 } | ConvertTo-Json) | Out-Null
$rateKey = "price-tracker:rate-limit:$($register.data.id):/api/watchlist/$watchlistId"
docker exec price-tracker-redis redis-cli GET $rateKey
docker exec price-tracker-redis redis-cli TTL $rateKey
```

预期计数至少为 1，TTL 为 1～60 秒。窗口内超过 60 次时接口返回业务错误 `request too frequent`。Redis 计数异常或不可用时当前没有 fail-open 逻辑，请求会失败。

## 7. Producer 幂等 key

关注目标价设为高于当前价格，然后执行手动刷新，直到 Mock 价格产生变化：

```powershell
$producerPattern = "price-tracker:idempotent:notify:$($register.data.id):$productId:*"
docker exec price-tracker-redis redis-cli --scan --pattern $producerPattern
```

预期出现包含当前目标价格文本的 key，TTL 初始不超过 600 秒。TTL 内同一 `userId + productId + targetPrice` 再次命中时不再发送消息。该 key 在发送调用抛异常时删除；没有 publisher confirm 时，调用未抛异常但 Broker 未可靠接收的窗口仍可能保留 key并漏发。

## 8. Consumer 幂等 key

正常通知消费后，从 Producer/Consumer 日志取得 `messageId`：

```powershell
$messageId = "<日志中的 messageId>"
docker exec price-tracker-redis redis-cli GET "price-tracker:idempotent:notify:mq:$messageId"
docker exec price-tracker-redis redis-cli TTL "price-tracker:idempotent:notify:mq:$messageId"
```

预期值存在，TTL 初始不超过 1800 秒。相同 `messageId` 再次投递时日志出现 `decision=ack_skip`，不会再次调用通知服务。业务处理抛异常时 Consumer 删除 key 并重新抛出，以允许重试；进程在通知事务提交后、ACK 前崩溃时，Redis key 通常能拦截重投，但 Redis key 丢失或 TTL 到期后仍依赖 `last_notified_price`，不构成 exactly-once 保证。

## 验收结论标准

- key 名称必须与本页完全一致，不以模糊前缀代替具体值。
- TTL 为动态值，检查范围而不是固定秒数。
- 缓存 key 应在读后出现、写后删除；锁应短暂出现并最终释放。
- Redis 不可用会影响缓存、锁、限流和消息幂等，当前未实现统一降级策略。
