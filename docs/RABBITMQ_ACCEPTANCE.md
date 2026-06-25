# RabbitMQ 验收

本文描述当前价格提醒消息拓扑、正常链路、重试/DLQ、重复消息与 TraceId 验收。RabbitMQ Management UI 为 <http://localhost:15672>，默认凭据来自 `.env` 的 `guest/guest`。

## 拓扑

| 角色 | 名称 | 类型/属性 | 路由 |
| --- | --- | --- | --- |
| 主 Exchange | `price.alert.exchange` | durable direct | routing key `price.alert` |
| 主 Queue | `price.alert.queue` | durable | 绑定主 Exchange |
| DLX | `price.alert.dlx` | durable direct | routing key `price.alert.dlq` |
| DLQ | `price.alert.dlq` | durable | 绑定 DLX |

主队列参数：

```text
x-dead-letter-exchange = price.alert.dlx
x-dead-letter-routing-key = price.alert.dlq
```

检查命令：

```powershell
docker exec price-tracker-rabbitmq rabbitmqctl list_exchanges name type durable
docker exec price-tracker-rabbitmq rabbitmqctl list_queues name durable messages consumers arguments
docker exec price-tracker-rabbitmq rabbitmqctl list_bindings source_name destination_name routing_key
```

## 消息结构

`PriceAlertMessage` 包含 `messageId`、`userId`、`productId`、`watchlistId`、`currentPrice`、`targetPrice`、`productName`、`triggeredAt`。消息使用 Jackson JSON 转换。Producer 额外把 HTTP/MDC 中的 `X-Trace-Id` 写入 header。

## 1. 正常发送和消费

先按 [LOCAL_RUN_AND_ACCEPTANCE.md](LOCAL_RUN_AND_ACCEPTANCE.md) 创建商品与关注，目标价设为 `999999.00`，手动刷新直到价格发生变化。

验收证据：

1. Producer 日志包含 `Publishing price alert message` 和 `Published ... successfully`，记录同一个 `messageId`。
2. Consumer 日志包含 `Received`、`Start processing`、`Notification send success`。
3. `tb_notification` 新增一行，`send_status=1`、`is_read=0`。
4. `tb_watchlist.last_notified_price` 更新为消息中的 `currentPrice`。
5. 主队列最终无积压；Consumer 正常在线时消息可能瞬间完成，UI 看不到 Ready 消息属于正常情况。

```powershell
docker exec price-tracker-rabbitmq rabbitmqctl list_queues name messages_ready messages_unacknowledged consumers
docker exec price-tracker-mysql mysql -uroot -p123456 price_tracker -e "SELECT id,user_id,product_id,watchlist_id,is_read,send_status FROM tb_notification ORDER BY id DESC LIMIT 5;"
```

## 2. 消费失败重试与最终进入 DLQ

项目当前没有故障注入业务接口。专项验收通过 RabbitMQ Management HTTP API 向主 Exchange 发布字段缺失的 JSON；Consumer 校验失败会抛异常。默认监听器策略为最多 3 次尝试，初始间隔 1 秒、倍数 2、最大间隔 10 秒，最终拒绝且不重新入队，由 DLX 路由到 DLQ。

先记录 DLQ 数量：

```powershell
$credential = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("guest:guest"))
$mqHeaders = @{ Authorization = "Basic $credential" }
$before = Invoke-RestMethod -Headers $mqHeaders "http://localhost:15672/api/queues/%2F/price.alert.dlq"
```

发布非法业务消息。`payload` 是被二次编码的 JSON 字符串，这是 Management API 的要求：

```powershell
$badMessage = @{ messageId="accept-dlq-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"; productName="invalid" } | ConvertTo-Json -Compress
$publishBody = @{
  properties = @{
    content_type = "application/json"
    headers = @{ "X-Trace-Id" = "accept-dlq-trace" }
    delivery_mode = 2
  }
  routing_key = "price.alert"
  payload = $badMessage
  payload_encoding = "string"
} | ConvertTo-Json -Depth 10
Invoke-RestMethod -Method Post -Headers $mqHeaders -ContentType "application/json" `
  -Uri "http://localhost:15672/api/exchanges/%2F/price.alert.exchange/publish" -Body $publishBody

Start-Sleep -Seconds 8
$after = Invoke-RestMethod -Headers $mqHeaders "http://localhost:15672/api/queues/%2F/price.alert.dlq"
[pscustomobject]@{ before=$before.messages; after=$after.messages } | ConvertTo-Json
```

预期发布响应 `routed=true`；日志对同一消息出现 3 次失败处理，随后 `after >= before + 1`。DLQ 消息 header 中应有 `x-death`，并保留自定义 `X-Trace-Id`。若 Consumer 未启动，消息只会停留在主队列，不会触发消费重试或 DLQ。

查看 DLQ 消息但不确认：

```powershell
$getBody = @{ count=1; ackmode="ack_requeue_true"; encoding="auto"; truncate=50000 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Headers $mqHeaders -ContentType "application/json" `
  -Uri "http://localhost:15672/api/queues/%2F/price.alert.dlq/get" -Body $getBody | ConvertTo-Json -Depth 20
```

当前没有 DLQ 重放接口、后台补偿任务或自动告警；进入 DLQ 后需要人工检查和处置。

## 3. 重复 messageId 幂等

使用一个当前有效、通知开启且尚未以该价格通知过的关注记录。下面的 `$userId`、`$productId`、`$watchlistId` 必须替换为数据库实际值，`currentPrice` 必须小于等于 `targetPrice`。

```powershell
$fixedMessageId = "accept-duplicate-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
$message = @{
  messageId = $fixedMessageId
  userId = [long]$userId
  productId = [long]$productId
  watchlistId = [long]$watchlistId
  currentPrice = 88.88
  targetPrice = 999999.00
  productName = "Duplicate Acceptance"
  triggeredAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
} | ConvertTo-Json -Compress
$body = @{
  properties = @{ content_type="application/json"; delivery_mode=2; headers=@{ "X-Trace-Id"="accept-duplicate-trace" } }
  routing_key = "price.alert"
  payload = $message
  payload_encoding = "string"
} | ConvertTo-Json -Depth 10

1..2 | ForEach-Object {
  Invoke-RestMethod -Method Post -Headers $mqHeaders -ContentType "application/json" `
    -Uri "http://localhost:15672/api/exchanges/%2F/price.alert.exchange/publish" -Body $body
}
Start-Sleep -Seconds 2
docker exec price-tracker-redis redis-cli GET "price-tracker:idempotent:notify:mq:$fixedMessageId"
```

预期两次均被路由，第一条执行通知服务，第二条日志出现 `decision=ack_skip`；Redis key 存在且数据库最多新增一条对应通知。若该关注的 `last_notified_price` 已经是 `88.88`，第一条也会被业务防重跳过，因此测试前应使用一个新价格或将关注目标重新更新以清空 `last_notified_price`。Consumer 幂等 key TTL 默认 30 分钟，过期后重复消息仍可能进入业务层。

## 4. TraceId：HTTP -> MQ -> Consumer

对手动刷新请求显式传入唯一 header：

```powershell
$traceId = "accept-trace-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
$traceHeaders = @{ Authorization="Bearer $token"; "X-Trace-Id"=$traceId }
Invoke-RestMethod -Method Post -Uri "$base/api/internal/products/$productId/refresh-price" -Headers $traceHeaders
```

在应用日志中搜索该值。只有刷新产生价格变化且命中目标价时才会发送 MQ；否则再次刷新。预期以下日志级别前缀都包含同一个 `[traceId:<值>]`：

```text
PriceAlertProducer  Publishing price alert message
PriceAlertProducer  Published price alert message successfully
PriceAlertConsumer  Received price alert message
PriceAlertConsumer  Notification send success
```

HTTP 响应 header 也应返回同一 `X-Trace-Id`。没有传入 header 时 `TraceIdFilter` 自动生成 32 位无连字符 UUID。脱离 HTTP 直接发布且不设置 header 时，Consumer 日志的 traceId 为空，这是当前设计。

## 可靠性边界

- 当前已实现 Consumer 重试、DLX/DLQ、Producer Redis 业务幂等、Consumer `messageId` 幂等和 `last_notified_price` 防重。
- 当前没有 publisher confirm 和 publisher return。Producer 调用不抛异常不证明 Broker 已确认、持久化或成功路由。
- 当前没有事务外盒或本地消息表。商品/历史写库与 MQ 发布不在同一原子事务中，存在“数据库已提交但消息未可靠送达”和“消息已发但外层事务回滚”的窗口。
- Redis 幂等都有 TTL，Redis 数据丢失、TTL 到期以及业务处理与 ACK 之间的崩溃窗口仍可能产生重复或遗漏。
- 因此当前不保证 exactly-once；应按至少一次投递环境中的尽力去重理解，并通过 DLQ 人工处置最终失败消息。
