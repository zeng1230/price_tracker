# Stage 5 交付证据

本文档记录 2026-05-07 执行的 Stage 5 Integration & Delivery 验证结果。

## 环境基线

| 检查项 | 结果 |
| --- | --- |
| Java | `java version "17.0.17"` |
| Maven Wrapper | 通过 `mvnw.cmd` 使用 Apache Maven `3.9.14` |
| MySQL | `localhost:3306` TCP 连通 |
| Redis | `127.0.0.1:6379` RESP `PING` 返回 `+PONG` |
| RabbitMQ | `127.0.0.1:5672` TCP 连通，Spring AMQP 成功连接 |
| 应用 | `http://localhost:8080` TCP 连通 |

运行时说明：当前 Windows 环境中，`localhost` 会优先解析到 IPv6。最终验证使用以下启动方式：

```powershell
$env:REDIS_HOST="127.0.0.1"
$env:RABBITMQ_HOST="127.0.0.1"
.\mvnw.cmd spring-boot:run
```

## 数据库初始化

本机 PATH 中没有 `mysql` CLI，因此 Stage 5 使用 JDBC runner 执行 SQL 检查：

```powershell
java -cp C:\Users\zeng\.m2\repository\com\mysql\mysql-connector-j\9.7.0\mysql-connector-j-9.7.0.jar docs\Stage5SqlRunner.java
```

观察到的输出：

```text
executed src/main/resources/sql/00_init_database.sql
skipped existing object from src/main/resources/sql/tb_user.sql: Table 'tb_user' already exists
skipped existing object from src/main/resources/sql/tb_product.sql: Table 'tb_product' already exists
skipped existing object from src/main/resources/sql/tb_price_history.sql: Table 'tb_price_history' already exists
skipped existing object from src/main/resources/sql/tb_watchlist.sql: Table 'tb_watchlist' already exists
skipped existing object from src/main/resources/sql/tb_notification.sql: Table 'tb_notification' already exists
table tb_notification
table tb_price_history
table tb_product
table tb_user
table tb_watchlist
watchlist_index uk_user_product.user_id
watchlist_index uk_user_product.product_id
```

结论：五张业务表存在，`tb_watchlist.uk_user_product` 唯一索引存在。

## 编译与测试证据

命令：

```powershell
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q test
```

结果：

- 编译退出码为 `0`。
- 全量测试退出码为 `0`。
- Redis 序列化配置修复后，Redis/Product 定向测试退出码为 `0`。
- RabbitMQ traceId header 传播补齐后，全量测试退出码为 `0`。

关键测试日志：

```text
Received price alert message from queue=price.alert.queue, messageId=msg-001, watchlistId=5, productId=1, userId=99
Notification send success, key=price-tracker:idempotent:notify:mq:msg-001
Exposing 1 endpoint beneath base path '/actuator'
```

## 全链路回归结果

执行命令：README 中的 `全链路 Demo` PowerShell 脚本。

最终运行输出：

```json
{
  "traceId": "stage5-final-204118",
  "username": "stage5_user_20260507204118",
  "userId": 7,
  "productId": 7,
  "watchlistId": 6,
  "notificationId": 9,
  "priceHistoryCount": 1,
  "notificationCount": 1,
  "registerCode": 200,
  "loginCode": 200,
  "meCode": 200,
  "productDetailCode": 200,
  "productPriceCode": 200,
  "watchAddCode": 200,
  "watchUpdateCode": 200,
  "refreshCode": 200
}
```

捕获 ID：

| 标识 | 值 |
| --- | --- |
| `traceId` | `stage5-final-204118` |
| `userId` | `7` |
| `productId` | `7` |
| `watchlistId` | `6` |
| `notificationId` | `9` |

## RabbitMQ 异步链路证据

最终验证中，`traceId=stage5-final-204118`、`userId=7`、`productId=7`、`watchlistId=6`，日志证明 HTTP producer 与 RabbitMQ consumer 使用同一个 traceId：

```text
INFO [traceId:stage5-final-204118] ... Publishing price alert message, messageId=7-6-f211ef7c-6996-496e-8e9f-d229805c084e, exchange=price.alert.exchange, routingKey=price.alert, watchlistId=6, productId=7, userId=7
INFO [traceId:stage5-final-204118] ... Published price alert message successfully, messageId=7-6-f211ef7c-6996-496e-8e9f-d229805c084e, routingKey=price.alert, watchlistId=6, productId=7, userId=7
INFO [traceId:stage5-final-204118] ... Received price alert message from queue=price.alert.queue, messageId=7-6-f211ef7c-6996-496e-8e9f-d229805c084e, watchlistId=6, productId=7, userId=7
INFO [traceId:stage5-final-204118] ... Created price alert notification, watchlistId=6, productId=7, userId=7
INFO [traceId:stage5-final-204118] ... Notification send success, key=price-tracker:idempotent:notify:mq:7-6-f211ef7c-6996-496e-8e9f-d229805c084e, messageId=7-6-f211ef7c-6996-496e-8e9f-d229805c084e, watchlistId=6, productId=7, userId=7
```

拓扑：

| 项 | 值 |
| --- | --- |
| Exchange | `price.alert.exchange` |
| Queue | `price.alert.queue` |
| Routing key | `price.alert` |
| 消息转换器 | `Jackson2JsonMessageConverter` |

## Redis 验证

Redis 协议检查：

```text
+PONG
```

已验证的 key 类型：

| Key | 含义 |
| --- | --- |
| `price-tracker:cache:product:detail:{productId}` | 商品详情缓存 |
| `price-tracker:cache:product:price:{productId}` | 商品价格缓存 |
| `price-tracker:cache:user:watchlist:{userId}` | 用户关注列表缓存 |
| `price-tracker:cache:null:{businessKey}` | 空值缓存 |
| `price-tracker:lock:{businessKey}` | 分布式锁 |
| `price-tracker:rate-limit:{userId}:{apiPath}` | 限流计数 |
| `price-tracker:idempotent:notify:{businessId}` | 通知幂等 |

Stage 5 修复点：Redis JSON 序列化已注册 Java Time 支持，`ProductDetailVo.lastCheckedAt`、`createdAt`、`updatedAt` 可以正常写入缓存。

## 可观测性证据

Health 命令：

```powershell
Invoke-RestMethod -Method Get "http://localhost:8080/actuator/health" | ConvertTo-Json -Depth 10
```

观察到的状态：

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "MySQL" } },
    "rabbit": { "status": "UP", "details": { "version": "3.13.7" } },
    "redis": { "status": "UP", "details": { "version": "8.6.2" } },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

统一异常检查：

```powershell
Invoke-RestMethod -Method Get "http://localhost:8080/api/products/0" `
  -Headers @{ "X-Trace-Id"="stage5-final-exception" }
```

观察到的响应体：

```json
{
  "code": 401,
  "message": "authorization header is missing or invalid"
}
```

trace 日志检查：

```text
INFO [traceId:stage5-final-204118] ... ProductServiceImpl : cache miss, key=price-tracker:cache:product:detail:7
INFO [traceId:stage5-final-204118] ... ProductServiceImpl : lock acquired, key=price-tracker:lock:product:detail:7
INFO [traceId:stage5-final-204118] ... ProductServiceImpl : db fallback, productId=7
```

## 已知限制

- 不包含前端。
- 不拆分微服务。
- 商品价格由 `PriceMockUtil` 生成。
- RabbitMQ 当前没有配置死信队列或重试队列。
- 通知渠道只实现站内通知落库。
- SQL 文件不完全幂等；建议使用干净数据库，或使用 Stage 5 JDBC runner。
