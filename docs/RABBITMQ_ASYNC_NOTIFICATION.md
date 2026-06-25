# RabbitMQ 异步通知验证

## 1. 拓扑

价格提醒通知链路使用一条 RabbitMQ direct 路由。

| 项 | 值 | 代码来源 |
| --- | --- | --- |
| Exchange | `price.alert.exchange` | `RabbitMQConfig.PRICE_ALERT_EXCHANGE` |
| Exchange 类型 | `DirectExchange` | `RabbitMQConfig.priceAlertExchange()` |
| Queue | `price.alert.queue` | `RabbitMQConfig.PRICE_ALERT_QUEUE` |
| Routing key | `price.alert` | `RabbitMQConfig.PRICE_ALERT_ROUTING_KEY` |
| Binding | `price.alert.queue` 使用 `price.alert` 绑定到 `price.alert.exchange` | `RabbitMQConfig.priceAlertBinding(...)` |
| 消息转换器 | `Jackson2JsonMessageConverter` | `RabbitMQConfig.rabbitMessageConverter()` |

## 2. 消息契约

`PriceAlertMessage` 是 MQ 消息体，不是数据库实体，由 RabbitMQ 消息转换器序列化为 JSON。

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `messageId` | `String` | 消息唯一标识，由 `PriceServiceImpl` 生成，格式为 `productId-watchlistId-uuid` |
| `userId` | `Long` | 关注记录所属用户 |
| `productId` | `Long` | 触发提醒的商品 |
| `watchlistId` | `Long` | 命中目标价规则的关注记录 |
| `currentPrice` | `BigDecimal` | 刷新后的商品价格 |
| `targetPrice` | `BigDecimal` | 用户设置的目标价 |
| `productName` | `String` | 商品名称，用于通知内容和日志 |
| `triggeredAt` | `LocalDateTime` | 价格提醒触发时间；消费侧为空时使用当前时间兜底 |

## 3. Producer 验证

触发条件：

1. `PriceServiceImpl.refreshProductPrice(productId)` 查询有效商品。
2. `PriceMockUtil` 生成的新价格发生变化。
3. 更新 `tb_product.current_price`，并写入 `tb_price_history`。
4. 查询该商品下 `status = 1` 且 `notify_enabled = 1` 的关注记录。
5. 当 `currentPrice <= targetPrice` 时，`sendAlertIfNotDuplicate(...)` 使用 Redis `setIfAbsent` 写入价格刷新侧幂等 key。
6. 如果 Redis key 获取成功，`PriceAlertProducer.send(...)` 将 `PriceAlertMessage` 发送到 `price.alert.exchange`，routing key 为 `price.alert`。
7. 如果当前 MDC 中存在 `traceId`，producer 会把它写入 RabbitMQ header `X-Trace-Id`。

已有测试覆盖：

- `PriceServiceImplTest.refreshProductPriceCreatesHistoryAndAlertMessageWhenTargetReached`
- `PriceServiceImplTest.refreshProductPriceRecordsHistoryButDoesNotSendAlertWhenChangedPriceIsAboveTarget`
- `PriceServiceImplTest.refreshProductPriceSkipsDuplicateAlertWhenIdempotentKeyAlreadyExists`

Producer 日志示例：

```text
Publishing price alert message, messageId=7-6-f211ef7c-6996-496e-8e9f-d229805c084e, exchange=price.alert.exchange, routingKey=price.alert, watchlistId=6, productId=7, userId=7, productName=Laptop, currentPrice=79.00, targetPrice=80.00
Published price alert message successfully, messageId=7-6-f211ef7c-6996-496e-8e9f-d229805c084e, routingKey=price.alert, watchlistId=6, productId=7, userId=7, productName=Laptop, currentPrice=79.00
```

Producer 日志包含 `userId`、`productId`、`watchlistId`、`messageId`。

## 4. Consumer 验证

消费路径：

1. `PriceAlertConsumer.consume(...)` 监听 `price.alert.queue`。
2. 如果 RabbitMQ header 中存在 `X-Trace-Id`，consumer 会恢复到 MDC。
3. 记录消息接收日志，包含 `messageId`、`watchlistId`、`productId`、`userId`、`currentPrice`、`targetPrice`。
4. 业务处理前写入 Redis 消费侧幂等 key。
5. 如果 key 已存在，记录 `decision=ack_skip` 并直接返回。
6. 如果 key 获取成功，调用 `NotificationService.consumePriceAlert(message)`。
7. 处理异常时记录堆栈和 `decision=ack_keep_idempotent_key_until_ttl`，当前实现不重新抛出异常。

已有测试覆盖：

- `PriceAlertConsumerTest.consumeDelegatesToNotificationService`
- `PriceAlertConsumerTest.consumeSkipsDuplicateMessageWhenIdempotentKeyAlreadyExists`
- `PriceAlertConsumerTest.consumeAcksBySkippingWhenIdempotentKeyIsHit`
- `PriceAlertConsumerTest.consumeLogsErrorAndDoesNotRethrowWhenNotificationHandlingFails`

Consumer 日志示例：

```text
Received price alert message from queue=price.alert.queue, messageId=msg-001, watchlistId=5, productId=1, userId=99, currentPrice=79.00, targetPrice=80.00
Start processing price alert message, key=price-tracker:idempotent:notify:mq:msg-001, messageId=msg-001, watchlistId=5, productId=1, userId=99
Notification send success, key=price-tracker:idempotent:notify:mq:msg-001, messageId=msg-001, watchlistId=5, productId=1, userId=99
Idempotent hit for price alert message, key=price-tracker:idempotent:notify:mq:msg-001, messageId=msg-001, watchlistId=5, productId=1, userId=99, decision=ack_skip
Notification send failed, key=price-tracker:idempotent:notify:mq:msg-001, messageId=msg-001, watchlistId=5, productId=1, userId=99, decision=ack_keep_idempotent_key_until_ttl
```

Consumer 日志包含 `userId`、`productId`、`watchlistId`、`messageId`。

## 5. 通知落库

`NotificationServiceImpl.consumePriceAlert(...)` 是通知落库边界。

插入前校验：

- 消息不为空。
- `userId`、`productId`、`watchlistId`、`currentPrice`、`targetPrice` 不为空。
- `tb_watchlist` 中存在对应记录。
- 关注记录有效：`status = 1`。
- 通知开关开启：`notify_enabled = 1`。
- 当前价格仍满足目标价：`currentPrice <= targetPrice`。
- `tb_watchlist.last_notified_price` 用于抑制同一价格重复通知。

插入行为：

- 向 `tb_notification` 插入一条记录。
- `notify_type = TARGET_PRICE_REACHED`。
- `is_read = 0`。
- `send_status = 1`。
- `created_at` 和 `sent_at` 使用 `triggeredAt`；为空时使用当前时间。
- 将 `tb_watchlist.last_notified_price` 更新为 `currentPrice`。

已有测试覆盖：

- `NotificationServiceImplTest.consumePriceAlertCreatesNotificationAndUpdatesWatchlistWhenNotDuplicate`
- `NotificationServiceImplTest.consumePriceAlertSkipsDuplicatePriceNotification`
- `NotificationServiceImplTest.consumePriceAlertSkipsNotificationWhenCurrentPriceIsAboveTarget`

落库成功日志示例：

```text
Created price alert notification, watchlistId=5, productId=1, userId=99
```

## 6. 已知限制

- 真实集成验证依赖 RabbitMQ 运行；单元测试使用 mock，不依赖真实 broker。
- 当前 listener 失败策略是记录日志后返回，尚未配置专用重试队列或死信队列。
- producer 侧和 consumer 侧 Redis 幂等可以减少重复处理，但 `tb_notification` 目前没有数据库级唯一约束。
- `last_notified_price` 只防止同一价格重复通知，不覆盖所有语义重复场景。
- `Jackson2JsonMessageConverter` 负责消息序列化，消息 schema 目前没有版本号。
