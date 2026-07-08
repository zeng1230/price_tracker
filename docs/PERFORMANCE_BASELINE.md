# 性能基线 (Performance Baseline)

本文档定义了在将项目视为可验证的发布候选版本 (release candidate) 之前需要测量的最低性能与容量基线。它目前还不包含实际测量的生产结果。以下所有数值阈值都是**暂定的，有待实测**。

## 负载测试目标

| 领域 | 目标 | 主要信号 |
| --- | --- | --- |
| 价格刷新吞吐量 | 了解单个应用实例可以稳定完成多少次单商品刷新。 | 请求成功/失败、`price_refresh_final_total`、提供商延迟。 |
| 发件箱中继吞吐量 | 了解 `PENDING` 状态的发件箱事件以多快的速度变为 `SENT`。 | `tb_outbox_event.status`、`outbox_relay_total`、RabbitMQ 发布确认。 |
| Webhook 交付吞吐量 | 了解 Webhook 交付以多快的速度发送到模拟 (mock) 端点。 | `tb_notification_delivery.status`、`notification_delivery_total`、模拟端点请求计数。 |
| Redis 限流 | 确认超出限制的请求被拒绝，且 TTL 行为稳定。 | HTTP 429、`rate_limit_block_total`、Redis TTL。 |
| RabbitMQ 积压恢复 | 确认在消费者恢复后，较短的队列积压能够被排空。 | 队列深度、死信队列 (DLQ) 计数、`price_alert_consume_total`。 |
| MySQL 核心查询 | 确认商品、监听列表、通知和价格历史查询使用了预期的索引。 | 查询延迟、`EXPLAIN`、慢查询日志 (若启用)。 |

## 推荐的测试方法

首先使用轻量级的验证。在当前阶段，不要引入复杂的负载测试平台。

- 用于可重复集成行为的 JUnit 和 Testcontainers 场景。
- 用于本地 HTTP 验收和简单吞吐量检查的 PowerShell 或 curl 循环。
- 可选使用 `hey` 或 `k6` 进行受控的本地 HTTP 负载测试。
- 使用 MySQL `EXPLAIN` 分析核心查询的执行计划。
- 使用 RabbitMQ 管理 UI 或 API 观察队列深度。
- 在有流量后，请求 `/actuator/prometheus` 获取应用指标。

## 最低通过标准

以下标准是首次测量基线的占位符。在受控运行后，必须用真实数值替换它们。

| 领域 | 暂定标准 | 当前结果 |
| --- | --- | --- |
| 价格刷新 | 单个实例完成 N 次管理员触发的刷新，无异常 5xx，并记录最终的成功/失败指标。 | 待实测。 |
| 发件箱中继 | 在本地 RabbitMQ 下，中继在 X 秒内处理 N 个 `PENDING` 事件。 | 待实测。 |
| Webhook 交付 | 中继在 X 秒内向本地模拟端点发送 N 个 Webhook 交付。 | 待实测。 |
| Redis 限流 | 重复的受保护写请求超出配置的限制并返回 429，而低于限制的有效请求正常通过。 | 待实测。 |
| RabbitMQ 积压 | 在人为制造短暂积压后，消费者能排空队列且死信队列没有异常增长。 | 待实测。 |
| MySQL 查询索引 | 商品页面、监听列表页面、通知页面和价格历史查询在测试数据集下使用核心索引。 | 待实测。 |

## 建议的本地场景

### 价格刷新循环

1. 启动 MySQL、Redis、RabbitMQ 和应用程序。
2. 创建一个管理员、一个用户、一个商品和一个监听列表。
3. 循环调用 `POST /api/admin/products/{productId}/refresh-price`。
4. 记录次数、耗时、成功/失败响应以及 `price_refresh_final_total`。

PowerShell 示例：

```powershell
$count = 100
$started = Get-Date
1..$count | ForEach-Object {
  Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8080/api/admin/products/$productId/refresh-price" `
    -Headers @{ Authorization = "Bearer $adminToken" }
}
$elapsed = (Get-Date) - $started
$elapsed
```

### 发件箱中继积压

1. 临时停止应用程序，或通过 `OUTBOX_RELAY_ENABLED=false` 禁用中继。
2. 生成符合警报条件的价格刷新。
3. 重新启用中继。
4. 测量数据行在 `PENDING` 或 `FAILED_RETRYABLE` 状态下停留的时间。

### Webhook 交付

1. 运行一个返回 2xx 的本地模拟 HTTP 端点。
2. 启动应用并配置 `WEBHOOK_ENABLED=true`、`WEBHOOK_URL` 和 `WEBHOOK_SECRET`。
3. 生成警报通知。
4. 确认交付状态变为 `SENT` 且模拟端点收到带签名的请求。

### Redis 限流

1. 使用普通用户 Token。
2. 重复发送 `POST /api/watchlist`、`PUT /api/watchlist/{id}` 或 `DELETE /api/watchlist/{id}` 调用。
3. 确认在超出配置的阈值后出现 429。

### RabbitMQ 积压恢复

1. 在 RabbitMQ 管理后台观察 `price.alert.queue` 队列深度。
2. 通过暂停消费者或生成警报的速度快于消费者的处理速度，来制造短暂的积压。
3. 恢复正常消费。
4. 确认队列深度恢复到零或预期的空闲水平，且死信队列 (DLQ) 没有异常增长。

### MySQL 核心查询

对以下典型查询运行 `EXPLAIN`：

- 商品页面查询
- 按用户查询监听列表
- 按用户查询通知
- 按商品 and 捕获时间查询价格历史
- DEAD 发件箱 and DEAD Webhook 交付查询

记录数据集大小、查询计划、使用的索引以及观察到的延迟。若没有这些凭证，不要声称已建立容量基线。

## 报告模板

首次测量结果请使用以下格式：

```text
日期:
Git Commit:
机器配置:
JDK 版本:
数据库:
Redis:
RabbitMQ:
应用 Profile:
数据集大小:
测试场景:
输入速率:
持续时间:
成功次数:
失败次数:
P50/P95/P99 延迟:
队列深度 (前/后):
数据库观察:
Redis 观察:
结论:
```
