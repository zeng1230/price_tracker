# Price Tracker

Price Tracker 是一个基于 Spring Boot 3 的商品价格跟踪后端项目，覆盖用户认证、商品管理、关注列表、价格刷新、价格历史、站内通知，以及基于 Redis 和 RabbitMQ 的缓存、限流、幂等与异步通知链路。本项目保持单体后端形态，不包含前端，也不拆分微服务。

## 技术栈

- Java 17
- Maven Wrapper
- Spring Boot 3.3.4
- Spring Validation
- Spring Boot Actuator
- MyBatis Plus
- MySQL 8
- Redis
- RabbitMQ
- JWT
- Lombok
- Knife4j

## 本地依赖

默认本地服务如下：

| 依赖 | 默认值 |
| --- | --- |
| MySQL | `localhost:3306`，数据库 `price_tracker`，用户 `root`，密码 `123456` |
| Redis | `localhost:6379`，数据库 `0` |
| RabbitMQ | `localhost:5672`，用户 `guest`，密码 `guest`，vhost `/` |
| 应用地址 | `http://localhost:8080` |
| Knife4j | `http://localhost:8080/doc.html` |
| Health | `http://localhost:8080/actuator/health` |

在当前 Windows 环境中，`localhost` 可能优先解析到 IPv6。如果端口可连但应用连接 Redis 或 RabbitMQ 失败，建议启动前显式指定 IPv4：

```powershell
$env:REDIS_HOST="127.0.0.1"
$env:RABBITMQ_HOST="127.0.0.1"
.\mvnw.cmd spring-boot:run
```

## 配置变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `REDIS_HOST` | `localhost` | Redis 主机 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | 空 | Redis 密码 |
| `REDIS_DATABASE` | `0` | Redis 数据库编号 |
| `REDIS_TIMEOUT` | `5s` | Redis 超时时间 |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ 主机 |
| `RABBITMQ_PORT` | `5672` | RabbitMQ 端口 |
| `RABBITMQ_USERNAME` | `guest` | RabbitMQ 用户名 |
| `RABBITMQ_PASSWORD` | `guest` | RabbitMQ 密码 |
| `RABBITMQ_VIRTUAL_HOST` | `/` | RabbitMQ vhost |
| `RATE_LIMIT_DEFAULT_LIMIT` | `60` | 默认限流次数 |
| `RATE_LIMIT_DEFAULT_WINDOW_SECONDS` | `60` | 默认限流窗口秒数 |
| `NOTIFICATION_IDEMPOTENT_TTL_MINUTES` | `10` | 价格刷新侧通知幂等 TTL |
| `NOTIFICATION_CONSUMER_IDEMPOTENT_TTL_MINUTES` | `30` | MQ 消费侧幂等 TTL |
| `PRICE_REFRESH_BATCH_SIZE` | `100` | 定时价格刷新批大小 |

## 数据库初始化

SQL 文件位于 `src/main/resources/sql/`，建议按以下顺序执行：

```powershell
mysql -uroot -p123456 < src/main/resources/sql/00_init_database.sql
mysql -uroot -p123456 price_tracker < src/main/resources/sql/tb_user.sql
mysql -uroot -p123456 price_tracker < src/main/resources/sql/tb_product.sql
mysql -uroot -p123456 price_tracker < src/main/resources/sql/tb_price_history.sql
mysql -uroot -p123456 price_tracker < src/main/resources/sql/tb_watchlist.sql
mysql -uroot -p123456 price_tracker < src/main/resources/sql/tb_notification.sql
```

如果本机没有安装 MySQL CLI，可使用 Stage 5 JDBC runner：

```powershell
java -cp C:\Users\zeng\.m2\repository\com\mysql\mysql-connector-j\9.7.0\mysql-connector-j-9.7.0.jar docs\Stage5SqlRunner.java
```

表职责：

| 表 | 实体 | 职责 |
| --- | --- | --- |
| `tb_user` | `User` | 用户注册信息和登录身份 |
| `tb_product` | `Product` | 商品基础信息和当前价格 |
| `tb_price_history` | `PriceHistory` | 商品价格变化历史 |
| `tb_watchlist` | `Watchlist` | 用户关注、目标价、通知开关 |
| `tb_notification` | `Notification` | 站内价格提醒通知 |

`tb_watchlist.uk_user_product` 用于保证同一用户对同一商品的重复关注幂等。

## 编译、测试、启动

```powershell
java -version
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q test
```

```powershell
$env:REDIS_HOST="127.0.0.1"
$env:RABBITMQ_HOST="127.0.0.1"
.\mvnw.cmd spring-boot:run
```

## 核心 API

| API | 说明 |
| --- | --- |
| `POST /api/auth/register` | 注册用户 |
| `POST /api/auth/login` | 登录并返回 JWT |
| `GET /api/users/me` | 查询当前用户 |
| `POST /api/products` | 创建商品 |
| `GET /api/products/{id}` | 查询商品详情 |
| `GET /api/products/{id}/price` | 查询当前价格 |
| `GET /api/products` | 分页查询商品 |
| `PUT /api/products/{id}` | 更新商品 |
| `DELETE /api/products/{id}` | 逻辑删除商品 |
| `POST /api/watchlist` | 添加关注 |
| `GET /api/watchlist/my` | 查询当前用户关注列表 |
| `PUT /api/watchlist/{id}` | 修改目标价和通知开关 |
| `DELETE /api/watchlist/{id}` | 取消关注 |
| `GET /api/products/{id}/price-history` | 查询价格历史 |
| `GET /api/notifications/my` | 查询当前用户通知 |
| `PUT /api/notifications/{id}/read` | 标记通知已读 |
| `POST /api/internal/products/{id}/refresh-price` | 触发内部价格刷新 |

除 `/api/auth/**` 外，所有 `/api/**` 接口都需要 `Authorization: Bearer <token>`。

## Redis Key

MySQL 是数据源头，Redis 用于缓存、分布式锁、限流和幂等。

| Key | 含义 | 失效规则 |
| --- | --- | --- |
| `price-tracker:cache:product:detail:{productId}` | 商品详情缓存 | 商品更新、删除、价格刷新 |
| `price-tracker:cache:product:price:{productId}` | 商品价格缓存 | 商品更新、删除、价格刷新 |
| `price-tracker:cache:user:watchlist:{userId}` | 用户关注列表缓存 | 关注新增、更新、删除 |
| `price-tracker:cache:null:{businessKey}` | 空值缓存 | 短 TTL 自动失效 |
| `price-tracker:lock:{businessKey}` | Redis 分布式锁 | 锁 TTL 自动失效 |
| `price-tracker:rate-limit:{userId}:{apiPath}` | 接口限流计数 | 窗口 TTL 自动失效 |
| `price-tracker:idempotent:notify:{businessId}` | 通知幂等 key | TTL 自动失效 |

更多 key、TTL 和失效规则见 `docs/REDIS_KEY_CACHE_VALIDATION.md`。

## RabbitMQ 异步链路

拓扑：

| 项 | 值 |
| --- | --- |
| Exchange | `price.alert.exchange` |
| Exchange 类型 | `DirectExchange` |
| Queue | `price.alert.queue` |
| Routing key | `price.alert` |
| 消息转换器 | `Jackson2JsonMessageConverter` |
| Producer | `PriceAlertProducer` |
| Consumer | `PriceAlertConsumer` |
| 落库处理 | `NotificationServiceImpl.consumePriceAlert` |

`PriceAlertMessage` 字段：

- `messageId`
- `userId`
- `productId`
- `watchlistId`
- `currentPrice`
- `targetPrice`
- `productName`
- `triggeredAt`

成功异步通知的关键日志：

```text
Publishing price alert message
Published price alert message successfully
Received price alert message from queue=price.alert.queue
Created price alert notification
Notification send success
```

更多拓扑、消息字段和消费限制见 `docs/RABBITMQ_ASYNC_NOTIFICATION.md`。

## 全链路 Demo

先启动应用，再执行：

```powershell
$ErrorActionPreference = "Stop"
$base = "http://localhost:8080"
$username = "stage5_user_" + (Get-Date -Format "yyyyMMddHHmmss")
$password = "123456"
$trace = "stage5-e2e-" + (Get-Date -Format "HHmmss")
$commonHeaders = @{ "X-Trace-Id" = $trace }

$register = Invoke-RestMethod -Method Post "$base/api/auth/register" -Headers $commonHeaders `
  -ContentType "application/json" `
  -Body (@{ username=$username; password=$password; email="$username@example.com"; nickname="Stage5 User" } | ConvertTo-Json)

$login = Invoke-RestMethod -Method Post "$base/api/auth/login" -Headers $commonHeaders `
  -ContentType "application/json" `
  -Body (@{ username=$username; password=$password } | ConvertTo-Json)

$headers = @{ Authorization = "Bearer " + $login.data.token; "X-Trace-Id" = $trace }
$me = Invoke-RestMethod -Method Get "$base/api/users/me" -Headers $headers

$product = Invoke-RestMethod -Method Post "$base/api/products" -Headers $headers `
  -ContentType "application/json" `
  -Body (@{
    productName="Stage5 Demo Product"
    productUrl="https://example.com/stage5-product-$username"
    platform="demo"
    currentPrice=100.00
    currency="USD"
    imageUrl="https://example.com/stage5-product.png"
  } | ConvertTo-Json)
$productId = $product.data

Invoke-RestMethod -Method Get "$base/api/products/$productId" -Headers $headers
Invoke-RestMethod -Method Get "$base/api/products/$productId/price" -Headers $headers

$watch = Invoke-RestMethod -Method Post "$base/api/watchlist" -Headers $headers `
  -ContentType "application/json" `
  -Body (@{ productId=$productId; targetPrice=200.00; notifyEnabled=1 } | ConvertTo-Json)
$watchlistId = $watch.data

Invoke-RestMethod -Method Put "$base/api/watchlist/$watchlistId" -Headers $headers `
  -ContentType "application/json" `
  -Body (@{ targetPrice=250.00; notifyEnabled=1 } | ConvertTo-Json)

Invoke-RestMethod -Method Post "$base/api/internal/products/$productId/refresh-price" -Headers $headers
Start-Sleep -Seconds 3

$history = Invoke-RestMethod -Method Get "$base/api/products/$productId/price-history?pageNum=1&pageSize=10" -Headers $headers
$notifications = Invoke-RestMethod -Method Get "$base/api/notifications/my?pageNum=1&pageSize=10" -Headers $headers

$notificationId = $null
if ($notifications.data.records.Count -gt 0) {
  $notificationId = $notifications.data.records[0].id
  Invoke-RestMethod -Method Put "$base/api/notifications/$notificationId/read" -Headers $headers
}

[PSCustomObject]@{
  traceId=$trace
  userId=$me.data.id
  productId=$productId
  watchlistId=$watchlistId
  notificationId=$notificationId
  priceHistoryCount=$history.data.records.Count
  notificationCount=$notifications.data.records.Count
}
```

## 可观测性

- `GlobalExceptionHandler` 统一处理业务异常、参数校验异常、约束异常和兜底异常，返回统一 `Result<T>` 结构。
- `TraceIdFilter` 读取 `X-Trace-Id`；如果请求没有传入，则自动生成 traceId。
- traceId 写入 MDC，并通过响应头 `X-Trace-Id` 返回。
- 日志通过 `logging.pattern.level` 输出 `traceId`。
- RabbitMQ producer 会把 traceId 写入消息 header，consumer 会从 header 恢复 traceId，保证异步日志可追踪。
- `/actuator/health` 暴露应用、MySQL、Redis、RabbitMQ、磁盘和 ping 状态。

Health 检查：

```powershell
Invoke-RestMethod -Method Get "http://localhost:8080/actuator/health" | ConvertTo-Json -Depth 10
```

trace 检查可以直接使用全链路 Demo 生成的 `$headers` 与 `$productId`：

```powershell
$headers["X-Trace-Id"] = "stage5-trace-check"
Invoke-RestMethod -Method Get "http://localhost:8080/api/products/$productId" -Headers $headers
```

## 已知限制

- 价格刷新使用 `PriceMockUtil`，没有接入真实电商爬虫或第三方价格接口。
- 通知只实现站内落库，没有实现邮件、短信、Push 等外部渠道。
- RabbitMQ 消费失败当前依赖日志和 Redis 幂等 TTL，没有配置 DLQ 或重试队列。
- 项目按单体 Spring Boot 后端交付，不包含前端或微服务拆分。
- SQL 文件是普通 DDL，不完全幂等；建议使用干净数据库，或使用 Stage 5 JDBC runner 的已存在表跳过能力。

## 交付文档

- Stage 5 交付证据：`docs/STAGE5_DELIVERY.md`
- RabbitMQ 异步通知验证：`docs/RABBITMQ_ASYNC_NOTIFICATION.md`
- Redis key 与缓存验证：`docs/REDIS_KEY_CACHE_VALIDATION.md`
- 项目复盘：`docs/PROJECT_REVIEW.md`
- 面试问答：`docs/INTERVIEW_QA.md`
- 压测方案：`docs/performance-test.md`
- 交接说明：`docs/STAGE_HANDOFF.md`
