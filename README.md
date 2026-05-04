# Price Tracker

Price Tracker 是一个基于 Spring Boot 3.x 的商品价格跟踪后端项目。当前实现聚焦用户认证、商品管理、关注列表、价格刷新、价格历史、站内通知、第二阶段 RabbitMQ 异步价格提醒链路，以及第三阶段 Redis 高并发增强能力。

## 核心功能

- 用户认证：支持注册、登录，并通过 JWT 保护需要登录的接口。
- 当前用户：支持查询当前登录用户信息。
- 商品管理：支持商品新增、详情查询、分页查询、更新和逻辑删除。
- 关注列表：用户可以关注商品，设置目标价和是否开启通知。
- 价格刷新：内部接口和定时任务可以触发商品价格刷新，当前价格由 mock 工具生成。
- 价格历史：商品价格发生变化后记录历史价格。
- 站内通知：用户可以分页查看自己的通知，并将通知标记为已读。
- 异步提醒：商品价格达到关注目标价时，通过 RabbitMQ 投递价格提醒消息，再由消费者创建通知。

## 技术栈

- Java 17
- Maven Wrapper
- Spring Boot 3.3.4
- Spring Validation
- MyBatis Plus
- MySQL 8
- Redis
- RabbitMQ
- JWT
- Lombok
- Knife4j

## 简历项目描述

Price Tracker 是一个基于 Spring Boot 3.x 的商品价格跟踪后端项目，实现了用户登录、商品管理、关注目标价、价格刷新、RabbitMQ 异步通知、Redis 缓存与幂等防重的完整链路。项目已完成真实端到端联调：注册/登录 -> 创建商品 -> 添加关注 -> 手动刷新价格 -> MQ 生产消费 -> 写入站内通知。

## 面试讲解入口

- 项目复盘：[docs/PROJECT_REVIEW.md](docs/PROJECT_REVIEW.md)
- 面试问答：[docs/INTERVIEW_QA.md](docs/INTERVIEW_QA.md)
- 可复现联调：见本文档 `Integration 最终验收`

## 模块说明

- `controller`：接收请求参数并统一返回 `Result<T>`，不直接暴露 entity。
- `service`：承载业务逻辑，包括商品、关注、通知、价格刷新和认证逻辑。
- `mapper`：基于 MyBatis Plus 访问数据库。
- `dto`：请求参数对象。
- `vo`：响应参数对象。
- `entity`：数据库表映射对象。
- `mq.message`：RabbitMQ 消息体，目前包含 `PriceAlertMessage`。
- `mq.producer`：价格提醒消息生产者。
- `mq.consumer`：价格提醒消息消费者。
- `config`：Web、MyBatis Plus、Redis、RabbitMQ、Knife4j、JWT 相关配置。
- `task`：定时价格刷新任务。

## RabbitMQ 异步通知链路

当前价格提醒链路已经从“刷新价格时直接写通知表”改为 RabbitMQ 异步处理。

1. `PriceServiceImpl.refreshProductPrice(productId)` 查询有效商品并生成新价格。
2. 如果价格未变化，只更新 `lastCheckedAt`，不记录价格历史，也不发送通知消息。
3. 如果价格发生变化，更新商品当前价和检查时间，并写入 `tb_price_history`。
4. 服务查询该商品下 `status = 1` 且 `notify_enabled = 1` 的关注记录。
5. 当 `target_price` 不为空且 `newPrice <= targetPrice` 时，构造 `PriceAlertMessage`。
6. `PriceAlertProducer` 将消息发送到 RabbitMQ。
7. `PriceAlertConsumer` 监听队列并调用 `NotificationService.consumePriceAlert(message)`。
8. `NotificationServiceImpl` 再次校验关注记录是否仍有效、是否仍开启通知、价格是否仍达到目标价。
9. 通过校验后写入 `tb_notification`，并把 `tb_watchlist.last_notified_price` 更新为当前触发价格。

### RabbitMQ 设计

- Exchange：`price.alert.exchange`
- Exchange 类型：`DirectExchange`
- Exchange 持久化：是
- Queue：`price.alert.queue`
- Queue 持久化：是
- Routing key：`price.alert`
- Binding：`price.alert.queue` 绑定到 `price.alert.exchange`，routing key 为 `price.alert`
- 消息序列化：`Jackson2JsonMessageConverter`
- 消费入口：`PriceAlertConsumer.consume`
- 业务处理入口：`NotificationService.consumePriceAlert`

### 消息体

`PriceAlertMessage` 当前字段如下：

- `messageId`：消息唯一标识，消费者优先基于该字段生成 Redis 幂等 key。
- `userId`：关注记录所属用户 ID。
- `productId`：触发价格提醒的商品 ID。
- `watchlistId`：触发价格提醒的关注记录 ID。
- `currentPrice`：刷新后的当前价格。
- `targetPrice`：用户设置的目标价格。
- `productName`：商品名称，用于生成通知内容。
- `triggeredAt`：触发时间，消费端为空时会使用当前时间兜底。

### 简单防重策略

当前防重策略在消费端执行：

- 消费者优先使用 `messageId` 生成 Redis 幂等 key；如果 `messageId` 为空，则使用用户、商品、目标价、当前价、触发时间等业务字段兜底。
- 如果 Redis 幂等 key 已存在，认为消息已处理或正在处理，直接跳过。
- 消费消息时重新查询 `watchlistId` 对应的关注记录。
- 如果关注记录不存在、`status != 1`、`notify_enabled != 1`，直接跳过。
- 如果 `currentPrice > targetPrice`，直接跳过。
- 如果 `watchlist.last_notified_price` 与消息中的 `currentPrice` 相等，认为同一价格已经通知过，直接跳过。
- 成功创建通知后，将 `last_notified_price` 更新为本次 `currentPrice`，用于后续去重。

当前尚未实现死信队列、延迟重试、幂等唯一索引、外部邮件或短信发送。

## 第三阶段：高并发增强版

第三阶段在现有单体 Spring Boot 项目上补充 Redis 缓存、分布式锁、接口限流、幂等保护和价格刷新批处理能力。该阶段不改变已有业务入口，重点降低热点查询和重复通知在高并发场景下对 MySQL、MQ 和通知链路的压力。

### Redis 缓存

- Redis 连接配置位于 `src/main/resources/application.yml`，支持通过 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`REDIS_DATABASE`、`REDIS_TIMEOUT` 覆盖默认值。
- 统一 Redis Key 由 `RedisKeyManager` 管理，前缀为 `price-tracker:`。
- `RedisCacheService` 提供 `get`、`set`、`delete`、`setIfAbsent` 和 TTL 随机偏移能力。
- 商品详情缓存 key：`price-tracker:cache:product:detail:{productId}`。
- 商品价格缓存 key：`price-tracker:cache:product:price:{productId}`。
- 用户关注列表缓存 key：`price-tracker:cache:user:watchlist:{userId}`。
- 空值缓存 key：`price-tracker:cache:null:{businessKey}`。

### 商品缓存策略

- 商品详情查询 `GET /api/products/{id}` 优先读 Redis，未命中时回源 MySQL 并写入缓存。
- 商品价格查询 `GET /api/products/{id}/price` 优先读 Redis，未命中时回源 MySQL 并写入缓存。
- 缓存穿透保护：不存在或非有效状态的商品会写入短 TTL 空值缓存，后续相同请求命中空值缓存后直接返回 `product not found`。
- 缓存击穿保护：商品详情和商品价格回源前会尝试获取 Redis 分布式锁，锁 key 使用 `price-tracker:lock:{businessKey}`。
- 缓存雪崩保护：正常商品缓存使用基础 TTL 加随机偏移，当前商品缓存基础 TTL 为 30 分钟，随机偏移最多 5 分钟。
- 缓存失效：商品更新、删除、价格刷新后会删除商品详情缓存、商品价格缓存和对应空值缓存。

### 关注与通知增强

- 用户关注列表查询 `GET /api/watchlist/my` 会优先读取 Redis 缓存，未命中时查询 MySQL 并回填缓存。
- 添加关注、修改目标价、取消关注后会删除当前用户的关注列表缓存。
- 重复关注幂等：如果用户已存在有效关注记录，重复添加时直接返回已有关注记录 ID。
- 通知入口幂等：价格刷新触发通知前使用 Redis `setIfAbsent` 写入通知幂等 key，默认 TTL 为 10 分钟。
- MQ 消费者幂等：消费者处理 `PriceAlertMessage` 前使用 messageId 或业务字段生成 Redis 幂等 key，默认 TTL 为 30 分钟；命中时跳过处理并记录日志。

### 接口限流

- `@RateLimit` 注解用于关注相关写接口：添加关注、修改目标价、取消关注。
- 限流维度为 `userId + apiPath`，key 格式为 `price-tracker:rate-limit:{userId}:{apiPath}`。
- 默认限流配置来自 `rate-limit.default-limit` 和 `rate-limit.default-window-seconds`，当前默认值为 60 次 / 60 秒。
- 超过限流阈值时返回 `TOO_MANY_REQUESTS` 对应错误。

### 价格刷新批处理

- 定时任务入口仍为 `PriceRefreshTask`，应用入口仍通过 `@EnableScheduling` 开启调度。
- `PriceRefreshTask` 调用 `PriceService.refreshActiveProducts()`。
- 批处理逻辑位于 `PriceServiceImpl.refreshActiveProducts()`，按商品 ID 升序分页扫描有效商品。
- 批大小由 `price-tracker.price-refresh.batch-size` 控制，默认值为 100。
- 单个商品刷新失败不会中断整体任务；单商品最多重试 2 次，失败后记录日志并继续处理后续商品。

### 第三阶段测试说明

- Redis 基础能力测试覆盖 key 生成、缓存读写、TTL 随机偏移、分布式锁和限流拦截。
- 商品测试覆盖商品详情缓存、商品价格缓存、空值缓存、回源加锁和缓存失效。
- 关注测试覆盖关注列表缓存命中、关注缓存失效和重复关注幂等。
- MQ 测试覆盖消费者幂等、重复消息跳过和消费失败日志处理。
- 价格刷新测试覆盖分页批处理、单商品失败隔离和重试次数。
- 压测方案位于 `docs/performance-test.md`，结果表格保留为“待填写”，需要在真实环境压测后补充。

### 第三阶段验收清单

- [ ] 商品详情缓存命中。
- [ ] 商品价格缓存命中。
- [ ] 不存在商品空值缓存生效。
- [ ] 热点商品击穿保护生效。
- [ ] 商品缓存 TTL 随机偏移生效，降低缓存雪崩风险。
- [ ] 关注列表缓存命中。
- [ ] 添加关注、修改目标价、取消关注后缓存失效。
- [ ] 重复关注幂等。
- [ ] 通知入口幂等。
- [ ] MQ 消费者幂等。
- [ ] 关注相关接口限流生效。
- [ ] 价格刷新分页批处理。
- [ ] 单商品刷新失败不影响整体任务。
- [ ] README 和 `docs/performance-test.md` 已更新。

## API 概览

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/users/me`
- `POST /api/products`
- `GET /api/products/{id}`
- `GET /api/products/{id}/price`
- `GET /api/products`
- `PUT /api/products/{id}`
- `DELETE /api/products/{id}`
- `POST /api/watchlist`
- `GET /api/watchlist/my`
- `PUT /api/watchlist/{id}`
- `DELETE /api/watchlist/{id}`
- `GET /api/products/{id}/price-history`
- `GET /api/notifications/my`
- `PUT /api/notifications/{id}/read`
- `POST /api/internal/products/{id}/refresh-price`

## 本地启动

### 依赖准备

1. 安装 Java 17。
2. 启动 MySQL 8，默认连接为 `localhost:3306`，用户名 `root`，密码 `123456`。
3. 启动 Redis，默认连接为 `localhost:6379`。
4. 启动 RabbitMQ，默认连接为 `localhost:5672`，用户名和密码均为 `guest`。
5. 创建数据库并执行表结构 SQL：

```powershell
mysql -uroot -p123456 < src/main/resources/sql/00_init_database.sql
mysql -uroot -p123456 price_tracker < src/main/resources/sql/tb_user.sql
mysql -uroot -p123456 price_tracker < src/main/resources/sql/tb_product.sql
mysql -uroot -p123456 price_tracker < src/main/resources/sql/tb_price_history.sql
mysql -uroot -p123456 price_tracker < src/main/resources/sql/tb_watchlist.sql
mysql -uroot -p123456 price_tracker < src/main/resources/sql/tb_notification.sql
```

### 配置覆盖

默认配置位于 `src/main/resources/application.yml`。本地端口或账号不同，可通过环境变量覆盖：

```powershell
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
$env:RABBITMQ_HOST="localhost"
$env:RABBITMQ_PORT="5672"
$env:RABBITMQ_USERNAME="guest"
$env:RABBITMQ_PASSWORD="guest"
$env:NOTIFICATION_IDEMPOTENT_TTL_MINUTES="10"
$env:NOTIFICATION_CONSUMER_IDEMPOTENT_TTL_MINUTES="30"
$env:PRICE_REFRESH_BATCH_SIZE="100"
```

### 编译、测试与启动

```powershell
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q test
```

```powershell
.\mvnw.cmd spring-boot:run
```

Knife4j 文档入口：`http://localhost:8080/doc.html`

### 完整链路联调

以下步骤用于跑通“登录 -> 创建商品 -> 关注商品并设置目标价 -> 触发价格刷新 -> RabbitMQ 消费通知消息”的主链路。命令使用 PowerShell，应用默认运行在 `http://localhost:8080`。

1. 注册并登录用户：

```powershell
$base="http://localhost:8080"
$username="integration_" + (Get-Date -Format "yyyyMMddHHmmss")
$password="123456"

Invoke-RestMethod -Method Post "$base/api/auth/register" `
  -ContentType "application/json" `
  -Body (@{ username=$username; password=$password; email="$username@example.com"; nickname="Integration User" } | ConvertTo-Json)

$login = Invoke-RestMethod -Method Post "$base/api/auth/login" `
  -ContentType "application/json" `
  -Body (@{ username=$username; password=$password } | ConvertTo-Json)
$headers = @{ Authorization = "Bearer " + $login.data.token }
```

2. 创建商品：

```powershell
$product = Invoke-RestMethod -Method Post "$base/api/products" `
  -Headers $headers `
  -ContentType "application/json" `
  -Body (@{
    productName="Integration Test Product"
    productUrl="https://example.com/integration-product"
    platform="test"
    currentPrice=100.00
    currency="USD"
  } | ConvertTo-Json)
$productId = $product.data
```

3. 关注商品并设置高于当前价的目标价，确保下一次刷新更容易触发提醒：

```powershell
$watch = Invoke-RestMethod -Method Post "$base/api/watchlist" `
  -Headers $headers `
  -ContentType "application/json" `
  -Body (@{ productId=$productId; targetPrice=200.00; notifyEnabled=1 } | ConvertTo-Json)
$watchlistId = $watch.data
```

4. 手动触发价格刷新：

```powershell
Invoke-RestMethod -Method Post "$base/api/internal/products/$productId/refresh-price" -Headers $headers
```

5. 验证结果：

```powershell
Invoke-RestMethod -Method Get "$base/api/products/$productId/price" -Headers $headers
Invoke-RestMethod -Method Get "$base/api/products/$productId/price-history?pageNum=1&pageSize=10" -Headers $headers
Invoke-RestMethod -Method Get "$base/api/notifications/my?pageNum=1&pageSize=10" -Headers $headers
```

如果 RabbitMQ 正常连通，应用日志应出现 `Received price alert message` 和 `Notification send success`，通知列表应能看到 `TARGET_PRICE_REACHED` 类型记录。若通知暂未出现，先确认 RabbitMQ 已启动且队列 `price.alert.queue` 中消息已被消费。

## Integration 最终验收

本节用于把已经跑通的端到端链路固化为可复现流程。示例基于当前接口、DTO、VO 和 `Result<T>` 返回结构。

### curl 完整链路

以下命令假设应用运行在 `http://localhost:8080`，MySQL、Redis、RabbitMQ 均已启动。

1. 注册用户

请求路径：`POST /api/auth/register`

```bash
curl -X POST "http://localhost:8080/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "e2e_user",
    "password": "123456",
    "email": "e2e_user@example.com",
    "nickname": "E2E User"
  }'
```

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "e2e_user",
    "email": "e2e_user@example.com",
    "nickname": "E2E User",
    "status": 1,
    "createdAt": "2026-05-02T11:28:21",
    "updatedAt": "2026-05-02T11:28:21"
  }
}
```

2. 登录获取 token

请求路径：`POST /api/auth/login`

```bash
curl -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "e2e_user",
    "password": "123456"
  }'
```

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

后续请求使用：

```bash
TOKEN="上一步返回的 data.token"
```

3. 创建商品

请求路径：`POST /api/products`

```bash
curl -X POST "http://localhost:8080/api/products" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "productName": "E2E Product",
    "productUrl": "https://example.com/e2e-product",
    "platform": "test",
    "currentPrice": 100.00,
    "currency": "USD"
  }'
```

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": 1
}
```

其中 `data` 是 `productId`。

4. 添加关注并设置目标价

请求路径：`POST /api/watchlist`

```bash
PRODUCT_ID=1

curl -X POST "http://localhost:8080/api/watchlist" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"productId\": $PRODUCT_ID,
    \"targetPrice\": 100000.00,
    \"notifyEnabled\": 1
  }"
```

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": 1
}
```

其中 `data` 是 `watchlistId`。示例使用很高的 `targetPrice`，目的是在 mock 价格发生变化时稳定触发提醒；真实业务中应设置用户期望的目标价。

5. 手动触发价格刷新

请求路径：`POST /api/internal/products/{id}/refresh-price`

```bash
curl -X POST "http://localhost:8080/api/internal/products/$PRODUCT_ID/refresh-price" \
  -H "Authorization: Bearer $TOKEN"
```

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

如果 `PriceMockUtil` 生成的新价格与旧价格相同，当前实现只更新 `lastCheckedAt`，不会写价格历史，也不会发送 MQ。可以重复触发刷新，直到价格变化后再验证后续链路。

6. 查询价格历史和通知

请求路径：`GET /api/products/{id}/price-history`

```bash
curl -X GET "http://localhost:8080/api/products/$PRODUCT_ID/price-history?pageNum=1&pageSize=10" \
  -H "Authorization: Bearer $TOKEN"
```

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "productId": 1,
        "oldPrice": 100.00,
        "newPrice": 88.00,
        "capturedAt": "2026-05-02T11:28:22",
        "source": "mock"
      }
    ],
    "total": 1,
    "current": 1,
    "size": 10,
    "pages": 1
  }
}
```

请求路径：`GET /api/notifications/my`

```bash
curl -X GET "http://localhost:8080/api/notifications/my?pageNum=1&pageSize=10" \
  -H "Authorization: Bearer $TOKEN"
```

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "productId": 1,
        "watchlistId": 1,
        "productName": "E2E Product",
        "notifyType": "TARGET_PRICE_REACHED",
        "content": "E2E Product current price 88.00 reached target 100000.00",
        "isRead": 0,
        "sendStatus": 1,
        "createdAt": "2026-05-02T11:28:22",
        "sentAt": "2026-05-02T11:28:22"
      }
    ],
    "total": 1,
    "current": 1,
    "size": 10,
    "pages": 1
  }
}
```

### 验证方式

MySQL 验证：

```sql
select id, product_id, old_price, new_price, captured_at, source
from tb_price_history
where product_id = 1
order by id desc;

select id, user_id, product_id, watchlist_id, notify_type, content, is_read, send_status, created_at, sent_at
from tb_notification
where product_id = 1
order by id desc;
```

预期结果：

- `tb_price_history` 至少出现一条当前商品的价格变化记录。
- `tb_notification` 出现 `notify_type = TARGET_PRICE_REACHED`、`send_status = 1` 的记录。

RabbitMQ 验证：

- Exchange：`price.alert.exchange`
- Queue：`price.alert.queue`
- Routing key：`price.alert`
- RabbitMQ Management UI 可查看 `price.alert.queue` 的消息进入和消费情况。正常消费很快时，`Ready` 可能为 `0`，这表示消息已被消费者取走，不代表未发送。
- 应用日志中应出现 Producer 和 Consumer 关键日志。

日志关键输出：

```text
Publishing price alert message
Published price alert message successfully
Received price alert message from queue=price.alert.queue
Created price alert notification
Notification send success
```

### 手动验证场景

1. 未达到目标价

操作步骤：

- 创建商品，设置 `currentPrice = 100.00`。
- 添加关注，设置 `targetPrice = 1.00`，`notifyEnabled = 1`。
- 调用 `POST /api/internal/products/{id}/refresh-price`。

预期结果：

- 价格变化时仍会写入 `tb_price_history`。
- 如果刷新后的 `currentPrice > targetPrice`，不应发送 MQ，不应写入 `tb_notification`。

实际验证方式：

- 查询 `tb_notification`，该 `product_id` 和 `watchlist_id` 不应新增 `TARGET_PRICE_REACHED`。
- 应用日志不应出现该商品对应的 `Publishing price alert message`。
- 单元测试 `PriceServiceImplTest.refreshProductPriceRecordsHistoryButDoesNotSendAlertWhenChangedPriceIsAboveTarget` 覆盖该逻辑。

2. 同一价格重复刷新

操作步骤：

- 让某个关注记录已经成功触发一次通知。
- 在 `tb_watchlist.last_notified_price` 已等于本次提醒价格后，再消费相同价格的 `PriceAlertMessage`。

预期结果：

- 不应重复插入通知。
- `NotificationServiceImpl` 会通过 `last_notified_price` 跳过重复通知。

实际验证方式：

- 查询 `tb_notification`，相同 `watchlist_id` 和相同价格不应重复增长。
- 应用日志应出现 `Skip duplicate price alert notification`。
- 单元测试 `NotificationServiceImplTest.consumePriceAlertSkipsDuplicatePriceNotification` 覆盖该逻辑。

3. MQ 消费异常

操作步骤：

- 当前项目没有提供运行时开关来主动让消费者抛异常。
- 使用现有测试模拟 `NotificationService.consumePriceAlert` 抛出异常：

```powershell
.\mvnw.cmd -q "-Dtest=PriceAlertConsumerTest#consumeLogsErrorAndDoesNotRethrowWhenNotificationHandlingFails" test
```

预期结果：

- Consumer 捕获异常并记录错误日志。
- 当前实现不会重新抛出异常触发 broker 重投。
- 当前实现没有死信队列和延迟重试机制。

实际验证方式：

- 测试应通过。
- 日志中应出现 `Notification send failed`。

4. 非法商品 ID

操作步骤：

```bash
curl -X POST "http://localhost:8080/api/internal/products/999999/refresh-price" \
  -H "Authorization: Bearer $TOKEN"
```

预期结果：

- `PriceServiceImpl.getActiveProductOrThrow` 查询不到有效商品。
- 返回统一错误结构。

响应示例：

```json
{
  "code": 404,
  "message": "product not found",
  "data": null
}
```

实际验证方式：

- HTTP 响应体包含 `code = 404` 和 `message = product not found`。
- 不应新增 `tb_price_history`。
- 不应发送 MQ。

### 系统链路说明

业务链路：用户注册登录后创建商品，关注商品并设置目标价。系统刷新商品价格时，如果新价格低于或等于目标价，就异步生成站内通知，用户可在通知列表中看到价格提醒。

技术链路：请求先进入 Controller，业务逻辑由 Service 处理，数据通过 MyBatis Plus Mapper 写入 MySQL。价格刷新由手动接口或定时任务触发，`PriceServiceImpl` 更新商品价格、写价格历史、判断目标价，满足条件后通过 `PriceAlertProducer` 投递 RabbitMQ。`PriceAlertConsumer` 消费消息后调用 `NotificationServiceImpl` 写入 `tb_notification` 并更新关注记录的最近通知价格。

核心设计点：

- Redis：用于商品详情/价格缓存、关注列表缓存、空值缓存、分布式锁、接口限流、通知入口幂等和 MQ 消费幂等。
- RabbitMQ：把价格刷新和通知落库解耦，避免通知处理阻塞价格刷新主流程。
- 幂等：发送前使用 Redis `setIfAbsent` 控制短时间重复触发；消费端使用 `messageId` 幂等 key 跳过重复消息；业务侧用 `last_notified_price` 避免同一价格重复通知。
- 日志：Producer、Consumer、通知落库、幂等跳过和异常路径都有关键日志，可据此定位消息是否发送、是否消费、是否落库以及在哪个环节中断。

## 项目亮点

- 统一包名 `com.example.price_tracker`，项目结构按 Spring Boot 单体应用组织。
- 统一使用 `Result<T>` 和 `PageResult<T>` 返回接口数据。
- DTO 和 VO 分离，控制器不直接返回 entity。
- 商品删除、关注删除采用状态字段实现逻辑删除。
- 价格提醒通过 RabbitMQ 异步解耦，价格刷新不直接承担通知落库逻辑。
- 消费端保留业务校验和简单防重，降低重复消息导致重复通知的概率。

## 后续规划

- 为 RabbitMQ 链路补充更完整的可靠性设计，例如消费重试、死信队列和异常消息观测。
- 为通知防重补充数据库级唯一约束或消息幂等表。
- 根据业务需要扩展通知发送渠道，目前只实现站内通知落库。
- 补充更完整的集成测试环境说明，例如 MySQL、Redis、RabbitMQ 的 Docker Compose 启动方式。
