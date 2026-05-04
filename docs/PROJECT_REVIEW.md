# Price Tracker 项目复盘

## 项目定位

Price Tracker 是一个基于 Spring Boot 3.x 的商品价格跟踪后端项目，用于演示从用户认证、商品管理、关注目标价、价格刷新、异步通知到站内通知落库的完整后端链路。项目采用单体架构，重点展示 MySQL 持久化、Redis 高并发增强、RabbitMQ 异步解耦、JWT 登录态和可验证的端到端联调。

## 核心业务链路

1. 用户注册并登录，系统返回 JWT。
2. 用户创建商品，记录商品名称、链接、平台、当前价格和币种。
3. 用户关注商品，设置 `targetPrice` 和 `notifyEnabled`。
4. 手动接口或定时任务触发价格刷新。
5. 价格变化后写入 `tb_price_history`。
6. 当新价格小于等于用户目标价时，发送 RabbitMQ 价格提醒消息。
7. Consumer 消费消息，校验关注记录仍有效后写入 `tb_notification`。
8. 用户通过通知列表查看价格提醒。

## 技术链路

HTTP 请求进入 Controller，Controller 只做参数接收和统一返回。Service 承载业务逻辑，Mapper 基于 MyBatis Plus 访问 MySQL。价格刷新链路中，`PriceServiceImpl` 更新商品价格、清理 Redis 商品缓存、写价格历史、判断目标价并投递 MQ。`PriceAlertConsumer` 监听 `price.alert.queue`，调用 `NotificationServiceImpl.consumePriceAlert` 写通知表并更新关注记录的 `last_notified_price`。

核心调用链：

```text
AuthController / ProductController / WatchlistController / InternalProductController
  -> Service
  -> Mapper / Redis / RabbitMQ
  -> PriceAlertConsumer
  -> NotificationServiceImpl
  -> tb_notification
```

## 数据库核心表设计

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `tb_user` | 用户账号和登录凭证 | `username`、`password`、`status` |
| `tb_product` | 商品基础信息和当前价格 | `product_name`、`current_price`、`status`、`last_checked_at` |
| `tb_watchlist` | 用户关注商品和目标价 | `user_id`、`product_id`、`target_price`、`notify_enabled`、`last_notified_price` |
| `tb_price_history` | 商品价格变化历史 | `product_id`、`old_price`、`new_price`、`captured_at` |
| `tb_notification` | 站内通知记录 | `user_id`、`product_id`、`watchlist_id`、`notify_type`、`send_status` |

`tb_watchlist` 使用 `uk_user_product` 唯一约束避免同一用户重复关注同一商品。商品和关注使用 `status` 做逻辑状态。

## Redis 使用点

- 商品详情缓存：`price-tracker:cache:product:detail:{productId}`。
- 商品价格缓存：`price-tracker:cache:product:price:{productId}`。
- 空值缓存：不存在或失效商品写短 TTL，降低缓存穿透。
- 分布式锁：商品缓存回源前加锁，降低热点 key 击穿风险。
- TTL 随机偏移：商品缓存和关注缓存增加随机 TTL，降低雪崩风险。
- 关注列表缓存：`price-tracker:cache:user:watchlist:{userId}`。
- 接口限流：关注写接口用 `userId + requestURI` 做固定窗口限流。
- 通知入口幂等：价格刷新发送 MQ 前使用 Redis `setIfAbsent`。
- MQ 消费幂等：Consumer 基于 `messageId` 或业务字段写 Redis 幂等 key。

## RabbitMQ 异步通知链路

RabbitMQ 配置：

- Exchange：`price.alert.exchange`
- Queue：`price.alert.queue`
- Routing key：`price.alert`
- Exchange 类型：`DirectExchange`
- 消息格式：`Jackson2JsonMessageConverter`

链路：

```text
PriceServiceImpl.refreshProductPrice
  -> shouldNotify(newPrice <= targetPrice)
  -> PriceAlertProducer.send
  -> price.alert.exchange
  -> price.alert.queue
  -> PriceAlertConsumer.consume
  -> NotificationServiceImpl.consumePriceAlert
  -> tb_notification
```

RabbitMQ 的作用是把价格刷新和通知落库解耦，避免通知处理阻塞刷新接口或定时任务。

## 幂等设计

1. 关注幂等：`tb_watchlist` 有 `uk_user_product`，Service 对已存在有效关注直接返回已有 ID；已取消关注会恢复。
2. 通知入口幂等：刷新价格达到目标价后，用 `userId:productId:targetPrice` 写 Redis 幂等 key，默认 TTL 10 分钟。
3. MQ 消费幂等：Consumer 优先使用 `messageId` 写 Redis 幂等 key，默认 TTL 30 分钟；重复消息直接跳过。
4. 业务防重：`NotificationServiceImpl` 比较 `watchlist.last_notified_price` 和消息当前价，同一价格已通知过则跳过。

当前没有数据库级通知唯一约束，也没有死信队列和延迟重试。

## 端到端联调验证结果

已在本机 Docker 启动 Redis 和 RabbitMQ 后跑通真实链路：

```text
注册/登录 -> 创建商品 -> 添加关注 -> 手动刷新价格 -> RabbitMQ Producer -> RabbitMQ Consumer -> 写入 tb_notification
```

一次真实验证结果：

- 商品：`productId = 2`
- 关注：`watchlistId = 2`
- 刷新后价格：`88.00`
- 价格历史：`priceHistoryCount = 1`
- 通知：`notificationId = 3`
- 通知类型：`TARGET_PRICE_REACHED`

日志证据：

```text
Publishing price alert message
Published price alert message successfully
Received price alert message from queue=price.alert.queue
Created price alert notification
Notification send success
```

验证命令详见 README 的 `Integration 最终验收`。

## 遇到的问题与解决方案

| 问题 | 原因 | 处理 |
| --- | --- | --- |
| RabbitMQ 未启动时测试日志报连接失败 | Spring Boot 上下文启动 Rabbit listener 会尝试连 broker | 区分单测通过和真实联调环境；真实联调前启动 RabbitMQ |
| Redis/RabbitMQ 环境不可复现 | 早期配置偏本地默认值 | `application.yml` 支持 Redis/RabbitMQ 环境变量覆盖 |
| 重复提醒风险 | 刷新任务、MQ 和 Consumer 都可能重复触发 | Redis 入口幂等、MQ 消费幂等、`last_notified_price` 业务防重 |
| 热点商品查询打到 MySQL | 商品详情和价格可能高频访问 | 商品缓存、空值缓存、分布式锁、TTL 随机偏移 |
| 单商品刷新失败影响批任务 | 批量刷新会扫描多个商品 | 单商品重试，失败后记录日志并继续后续商品 |

## 后续可扩展方向

- RabbitMQ 增加死信队列、延迟重试和异常消息观测。
- 通知链路增加数据库唯一约束或消息幂等表。
- 将 mock 价格刷新替换为真实电商价格采集任务。
- 增加邮件、短信、Webhook 等通知渠道。
- 增加 Docker Compose，统一 MySQL、Redis、RabbitMQ 本地启动。
- 对高频接口做真实压测，补充 QPS、P95、错误率和瓶颈分析。
- 定时刷新任务可拆分为分片任务或独立 worker，提高大商品量下的吞吐能力。
