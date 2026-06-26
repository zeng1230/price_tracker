# 本地启动与验收

本文给出 Windows PowerShell 下可复制的端到端验收流程。命令默认在仓库根目录执行，应用地址为 `http://localhost:8080`。

## 1. 启动依赖与应用

```powershell
Copy-Item .env.example .env
docker compose up -d
docker compose ps
docker exec price-tracker-mysql mysqladmin ping -uroot -p123456
docker exec price-tracker-redis redis-cli ping
docker exec price-tracker-rabbitmq rabbitmq-diagnostics -q ping
```

Docker Compose 只启动 MySQL、Redis 和 RabbitMQ。MySQL 容器会按 `MYSQL_DATABASE` 创建空 `price_tracker` database，但不会创建业务表；业务表和索引由 Spring Boot 启动时通过 Flyway 自动创建。只启动 MySQL、不启动应用时，数据库不会有 `tb_user`、`tb_product` 等业务表。

在另一个 PowerShell 窗口启动应用：

```powershell
$env:REDIS_HOST="127.0.0.1"
$env:RABBITMQ_HOST="127.0.0.1"
$env:RABBITMQ_USERNAME="guest"
$env:RABBITMQ_PASSWORD="guest"
./mvnw.cmd spring-boot:run
```

首次应用启动后，Flyway 会执行 `src/main/resources/db/migration/` 下的 migration。推荐 MySQL 8.x；当前 schema 使用 `CHECK` 约束，不为低版本 MySQL 降级语义。

## 2. 健康检查和 Knife4j

```powershell
$health = Invoke-RestMethod http://localhost:8080/actuator/health
$health | ConvertTo-Json -Depth 10
```

预期 `status=UP`，且 `components.db`、`components.redis`、`components.rabbit` 均为 `UP`。打开 <http://localhost:8080/doc.html> 验证 Knife4j；OpenAPI JSON 地址为 <http://localhost:8080/v3/api-docs>。

## 3. 跑通 HTTP、价格和通知链路

以下脚本会创建带时间戳的用户和商品，避免唯一键冲突。目标价设为 `999999.00`，保证只要 Mock 价格发生变化就满足通知阈值。Mock 可能随机得到与旧价格相同的值，因此脚本最多刷新 20 次并轮询异步通知。

```powershell
$base = "http://localhost:8080"
$stamp = Get-Date -Format "yyyyMMddHHmmssfff"
$traceId = "accept-$stamp"
$commonHeaders = @{ "X-Trace-Id" = $traceId }

$register = Invoke-RestMethod -Method Post -Uri "$base/api/auth/register" `
  -Headers $commonHeaders -ContentType "application/json" `
  -Body (@{
    username = "accept_$stamp"
    password = "Passw0rd!"
    email = "accept_$stamp@example.com"
    nickname = "Acceptance"
  } | ConvertTo-Json)

$login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" `
  -Headers $commonHeaders -ContentType "application/json" `
  -Body (@{ username = "accept_$stamp"; password = "Passw0rd!" } | ConvertTo-Json)
$token = $login.data.token
$headers = @{ Authorization = "Bearer $token"; "X-Trace-Id" = $traceId }

$product = Invoke-RestMethod -Method Post -Uri "$base/api/products" `
  -Headers $headers -ContentType "application/json" `
  -Body (@{
    productName = "Acceptance Product $stamp"
    productUrl = "https://example.com/products/$stamp"
    platform = "mock"
    currentPrice = 100.00
    currency = "CNY"
  } | ConvertTo-Json)
$productId = $product.data

$watch = Invoke-RestMethod -Method Post -Uri "$base/api/watchlist" `
  -Headers $headers -ContentType "application/json" `
  -Body (@{ productId = $productId; targetPrice = 999999.00; notifyEnabled = 1 } | ConvertTo-Json)
$watchlistId = $watch.data

$history = $null
for ($i = 1; $i -le 20; $i++) {
  Invoke-RestMethod -Method Post -Uri "$base/api/internal/products/$productId/refresh-price" -Headers $headers | Out-Null
  $history = Invoke-RestMethod -Method Get -Uri "$base/api/products/$productId/price-history?pageNum=1&pageSize=10" -Headers $headers
  if ($history.data.total -gt 0) { break }
}
if ($history.data.total -eq 0) { throw "Mock price did not change after 20 refresh attempts" }

$notifications = $null
for ($i = 1; $i -le 20; $i++) {
  $notifications = Invoke-RestMethod -Method Get -Uri "$base/api/notifications/my?pageNum=1&pageSize=10" -Headers $headers
  if ($notifications.data.total -gt 0) { break }
  Start-Sleep -Milliseconds 500
}
if ($notifications.data.total -eq 0) { throw "No notification consumed within 10 seconds" }
$notificationId = $notifications.data.records[0].id

Invoke-RestMethod -Method Put -Uri "$base/api/notifications/$notificationId/read" -Headers $headers | Out-Null
$notificationsAfterRead = Invoke-RestMethod -Method Get -Uri "$base/api/notifications/my?pageNum=1&pageSize=10" -Headers $headers

[pscustomobject]@{
  traceId = $traceId
  userId = $register.data.id
  productId = $productId
  watchlistId = $watchlistId
  priceHistoryCount = $history.data.total
  notificationId = $notificationId
  notificationRead = $notificationsAfterRead.data.records[0].isRead
} | ConvertTo-Json
```

预期所有业务响应 `code=200`，价格历史至少 1 条，通知至少 1 条，最后 `notificationRead=1`。用 `$traceId` 搜索应用日志，应能串联 HTTP、Producer 和 Consumer。

## 4. 验证 MySQL

将上一步 ID 保留在同一 PowerShell 会话中：

```powershell
docker exec price-tracker-mysql mysql -uroot -p123456 price_tracker -e "SELECT id,username,status FROM tb_user WHERE id=$($register.data.id);"
docker exec price-tracker-mysql mysql -uroot -p123456 price_tracker -e "SELECT id,current_price,last_checked_at,status FROM tb_product WHERE id=$productId;"
docker exec price-tracker-mysql mysql -uroot -p123456 price_tracker -e "SELECT id,old_price,new_price,source,captured_at FROM tb_price_history WHERE product_id=$productId ORDER BY id DESC;"
docker exec price-tracker-mysql mysql -uroot -p123456 price_tracker -e "SELECT id,user_id,product_id,target_price,notify_enabled,last_notified_price,status FROM tb_watchlist WHERE id=$watchlistId;"
docker exec price-tracker-mysql mysql -uroot -p123456 price_tracker -e "SELECT id,user_id,product_id,event_key,is_read,send_status,sent_at FROM tb_notification WHERE id=$notificationId;"
docker exec price-tracker-mysql mysql -uroot -p123456 price_tracker -e "SELECT installed_rank,version,description,success FROM flyway_schema_history ORDER BY installed_rank;"
```

预期商品有效、历史 `source=MOCK`、关注开启、`last_notified_price` 已更新，通知 `is_read=1`、`send_status=1`。
`flyway_schema_history` 应显示 V1 到最新版本均 `success=1`。

## 5. 验证 Redis

先触发三类读缓存：

```powershell
Invoke-RestMethod "$base/api/products/$productId" -Headers $headers | Out-Null
Invoke-RestMethod "$base/api/products/$productId/price" -Headers $headers | Out-Null
Invoke-RestMethod "$base/api/watchlist/my?pageNum=1&pageSize=10" -Headers $headers | Out-Null
docker exec price-tracker-redis redis-cli --scan --pattern "price-tracker:*"
```

至少应看到商品详情、价格、关注列表、限流和通知幂等 key。锁只在缓存 miss 重建期间短暂存在，不能把扫描不到锁视为失败。逐类验收和 TTL 命令见 [REDIS_ACCEPTANCE.md](REDIS_ACCEPTANCE.md)。

## 6. 验证 RabbitMQ

打开 <http://localhost:15672>，默认账号密码为 `.env` 中的 `guest/guest`。在 Exchanges、Queues 页面确认：

- `price.alert.exchange` 通过 routing key `price.alert` 绑定 `price.alert.queue`。
- `price.alert.queue` 参数包含 `x-dead-letter-exchange=price.alert.dlx` 和 `x-dead-letter-routing-key=price.alert.dlq`。
- `price.alert.dlx` 通过 `price.alert.dlq` 绑定 `price.alert.dlq`。
- 正常链路结束后主队列消息可被消费，通知写入 MySQL；DLQ 通常为 0。

命令行检查：

```powershell
docker exec price-tracker-rabbitmq rabbitmqctl list_exchanges name type durable
docker exec price-tracker-rabbitmq rabbitmqctl list_queues name messages consumers arguments
docker exec price-tracker-rabbitmq rabbitmqctl list_bindings source_name destination_name routing_key
```

重试、DLQ、重复 `messageId` 和 TraceId 的专项验收见 [RABBITMQ_ACCEPTANCE.md](RABBITMQ_ACCEPTANCE.md)。

## 7. 测试命令

```powershell
./mvnw.cmd -q -DskipTests compile
./mvnw.cmd -q test
```

测试通过证明代码级回归通过；本页的 HTTP、MySQL、Redis 和 RabbitMQ 检查用于证明运行时集成链路可复现，两者不能互相替代。

## 8. 价格趋势聚合验收

先确保 `$productId` 对应有效商品，并已通过价格刷新产生至少一条历史记录：

```powershell
$trend = Invoke-RestMethod -Method Get `
  -Uri "$base/api/products/$productId/price-trend" `
  -Headers $headers
$trend | ConvertTo-Json -Depth 10
```

预期 `code=200`，`data` 包含：

```text
productId
currency
currentPrice
lowestPrice7Days
lowestPrice30Days
historicalLowestPrice
historicalHighestPrice
averagePrice
priceChangeCount
differenceFromLowest
differenceFromLowestPercentage
lastPriceChangedAt
```

统计口径：

- 平均价样本严格等于第一条历史 `old_price` + 每条历史 `new_price` + 可选 `currentPrice`。
- `currentPrice` 等于最后一条 `new_price` 时不重复计入平均价；不同时追加为最新样本。
- 历史最低价和最高价始终包含 `currentPrice`。
- 近 7/30 天最低价取窗口内历史记录的 `old_price/new_price` 与 `currentPrice` 的最小值；窗口无历史时回退为 `currentPrice`。
- 这是价格变动样本近似口径，不是按价格持续时间加权的状态统计。

用 MySQL 手工核对样本：

```powershell
docker exec price-tracker-mysql mysql -uroot -p123456 price_tracker -e "
SELECT id,current_price,currency,status
FROM tb_product
WHERE id=$productId;

SELECT id,old_price,new_price,captured_at
FROM tb_price_history
WHERE product_id=$productId
ORDER BY captured_at ASC,id ASC;"
```

验证权限边界：

```powershell
# USER token 和 ADMIN token 均应返回 code=200
Invoke-RestMethod "$base/api/products/$productId/price-trend" -Headers $userHeaders
Invoke-RestMethod "$base/api/products/$productId/price-trend" -Headers $adminHeaders

# 不带 token 应返回 code=401
Invoke-RestMethod "$base/api/products/$productId/price-trend"
```

验证无当前价边界时，可准备一个有效但 `current_price IS NULL` 的测试商品。接口应返回 `code=1002` 和提示 `current price is not available; refresh the product price first`，不能将空价格按零处理。

执行计划优先使用 MySQL 8 的 `EXPLAIN ANALYZE`：

```sql
EXPLAIN ANALYZE
SELECT COUNT(*),
       SUM(new_price),
       MIN(CASE
               WHEN captured_at >= NOW() - INTERVAL 7 DAY
               THEN LEAST(COALESCE(old_price, new_price), new_price)
           END)
FROM tb_price_history
WHERE product_id = 1;
```

预期查询以 `product_id` 为过滤条件使用 `idx_price_history_product_captured_at`。如果运行环境不支持 `EXPLAIN ANALYZE`，改用 `EXPLAIN` 并在验收报告中注明降级。
## 9. Flyway、旧库升级和本地重建

新开发环境：

1. `docker compose up -d` 启动 MySQL、Redis、RabbitMQ。
2. `./mvnw.cmd spring-boot:run` 启动应用。
3. Flyway 从 V1 开始执行并创建业务表、增量字段和索引。
4. 用 `flyway_schema_history` 查询确认 migration 已成功执行。

旧本地 volume：

- 只有已经人工确认达到当前最终 schema 的旧库，才允许使用 `baselineVersion=4`。
- 缺 `tb_user.role`、缺 `tb_notification.event_key`、缺 `ux_notification_event_key` 或缺查询索引的旧库，不能直接 baseline 到 4。
- 本地开发库建议备份后重建；必须保留数据时，先手工补齐字段和索引，核验 `SHOW CREATE TABLE`、`SHOW COLUMNS`、`SHOW INDEX`，再 baseline 到 4。
- `baseline-on-migrate` 只应用于本地一次性升级场景，不应长期写入默认配置。

本地旧库一次性 baseline 示例：

```powershell
./mvnw.cmd spring-boot:run `
  -Dspring-boot.run.arguments="--spring.flyway.baseline-on-migrate=true --spring.flyway.baseline-version=4"
```

查看 Flyway 历史：

```powershell
docker exec price-tracker-mysql mysql -uroot -p123456 price_tracker -e "SELECT installed_rank,version,description,type,success FROM flyway_schema_history ORDER BY installed_rank;"
```

本地 reset：

1. 先确认是否需要备份数据。
2. 停止应用和依赖容器。
3. 删除项目的 MySQL Docker volume 后重新 `docker compose up -d`。
4. 再启动 Spring Boot，让 Flyway 在空库中重新执行 V1 到最新版本。

`src/main/resources/sql/` 已是 legacy/reference，不再用于 Docker 初始化。
