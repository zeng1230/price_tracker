# 价格追踪器 (Price Tracker)

## 当前能力

- 以后端 MySQL、Redis 和 RabbitMQ 为支撑的 Spring Boot 单体 REST API。
- 由 Flyway 管理的数据库架构，位于 `src/main/resources/db/migration` 下。
- 事务性发件箱表 (Transactional Outbox table) 以及用于价格警报事件的中继器 (relay)。
- 发件箱认领/租约字段 (`claim_owner`、`claimed_at`、`claimed_until`)，用以减少多实例部署时重复中继的工作。
- 支持 RabbitMQ 发布者确认及退回机制 (Confirm/Return)、主队列、死信交换机 (DLX) 和死信队列 (DLQ)。
- 支持应用内通知和 Webhook 通知交付任务。
- 提供了针对 DEAD 状态的发件箱事件和 Webhook 交付记录的管理员查询与重试端点。
- 实现了 `SerpApiPriceProvider`，但默认通过配置 `price-provider.serpapi.enabled=false` 禁用。
- `MockPriceProvider` 是默认的本地/测试回退方案，不会发起外部网络请求。
- 支持 JWT 登录/登出，并有基于 Redis 的 Token 黑名单支撑。
- `prod` profile 的敏感配置校验：会拒绝默认的或为空的敏感配置（如 JWT 密钥、MySQL 密码和 RabbitMQ 密码）。
- 支持 Actuator 健康检查端点以及 Prometheus 指标端点。
- 根目录提供了 `Dockerfile`，可将 Spring Boot jar 包打包为镜像。

## 技术栈

| 领域 | 技术 |
| --- | --- |
| 运行时 | Java 17, Spring Boot 3.3.4 |
| Web 层 | Spring MVC, Validation 校验, AOP 面向切面 |
| 持久层 | MySQL 8, MyBatis-Plus, Flyway 迁移 |
| 缓存与协调 | Redis 7, Spring Data Redis |
| 消息队列 | RabbitMQ 3 Management, Spring AMQP |
| 安全认证 | JWT, BCrypt 密码加密, Redis 黑名单 |
| 文档与可观测性 | Knife4j/OpenAPI, Actuator, Micrometer, Prometheus registry |
| 测试 | JUnit 5, Mockito, Spring Boot Test, Testcontainers |

## 架构设计

```text
HTTP 客户端
  -> Controller 控制器 / AuthInterceptor 拦截器 / TraceIdFilter 过滤器
  -> Service 业务服务层
     -> MyBatis-Plus -> MySQL
     -> Redis 缓存 / 分布式锁 / 限流 / 幂等键 / JWT 黑名单
     -> PriceProviderRouter 价格提供商路由
        -> SerpApiPriceProvider (当明确启用并匹配时)
        -> MockPriceProvider 回退默认
     -> tb_outbox_event (发件箱表)
        -> OutboxRelay 认领/租约
        -> RabbitMQ price.alert.exchange -> price.alert.queue
        -> PriceAlertConsumer (消费者)
        -> tb_notification (应用内通知表)
        -> (可选) tb_notification_delivery WEBHOOK 任务
        -> NotificationDeliveryRelay 认领/租约
        -> 配置的 Webhook 目标 URL
```

RabbitMQ 用于单体应用内部的异步解耦。这并不意味着项目已经被拆分为微服务。

## 核心业务流程

1. 用户注册并登录。
2. 在本地验收时，通过直接修改数据库操作将用户提升为 `ADMIN` 角色。
3. 管理员创建商品。
4. 用户创建商品监听项，设置目标价格 `targetPrice` 并启用通知 `notifyEnabled`。
5. 管理员触发 `POST /api/admin/products/{productId}/refresh-price` 进行价格刷新。
6. 价格提供商返回报价。默认由 `MockPriceProvider` 返回模拟数据。
7. 当价格发生改变时，系统将写入价格历史记录。
8. 如果当前价格达到用户的监听目标价格，系统将插入一条 `PENDING` 状态的发件箱事件。
9. `OutboxRelay` 认领就绪的事件并将其发布到 RabbitMQ。
10. `PriceAlertConsumer` 消费消息并创建应用内通知。
11. 如果 `notification.webhook.enabled=true`，则会插入 Webhook 交付任务，并由 `NotificationDeliveryRelay` 发送通知。
12. 运维人员可通过管理员端点查询并重试 DEAD 状态的发件箱事件或 Webhook 交付记录。

## 本地运行

前提条件：安装有 JDK 17 以及 Docker Desktop 或兼容的 Docker Compose 运行时。

```powershell
Copy-Item .env.example .env
docker compose up -d
docker compose ps

$env:REDIS_HOST="127.0.0.1"
$env:RABBITMQ_HOST="127.0.0.1"
.\mvnw.cmd spring-boot:run
```

`docker-compose.yml` 仅启动 MySQL、Redis 和 RabbitMQ，并不运行应用程序容器。业务表在 Spring Boot 启动时由 Flyway 自动创建。

## 本地管理员初始化

用户注册默认为 `USER` 角色。本系统未公开管理员注册 API。在本地验收时，请注册用户并手动提升其权限：

```powershell
docker exec price-tracker-mysql mysql -uroot -p123456 price_tracker `
  -e "UPDATE tb_user SET role='ADMIN' WHERE username='admin';"
```

修改角色后需重新登录，以确保 JWT 中包含 `ADMIN` 角色声明。

## API 概览

详细的 API 契约记录在 [docs/API_CONTRACT.md](docs/API_CONTRACT.md) 中。

核心端点：

| 模块 | 方法 | 路径 | 权限要求 |
| --- | --- | --- | --- |
| 认证 | POST | `/api/auth/register` | 公开 |
| 认证 | POST | `/api/auth/login` | 公开 |
| 认证 | POST | `/api/auth/logout` | 需 JWT |
| 商品 | POST | `/api/products` | 管理员 |
| 商品 | GET | `/api/products/{id}/price-history` | 需 JWT |
| 商品 | GET | `/api/products/{id}/price-trend` | 需 JWT |
| 监听列表 | POST | `/api/watchlist` | 需 JWT, 受限流控制 |
| 监听列表 | GET | `/api/watchlist/my` | 需 JWT |
| 通知 | GET | `/api/notifications/my` | 需 JWT |
| 通知 | PUT | `/api/notifications/{id}/read` | 需 JWT |
| 管理员 | POST | `/api/admin/products/{productId}/refresh-price` | 管理员 |
| 管理员 | GET | `/api/admin/outbox/dead` | 管理员 |
| 管理员 | POST | `/api/admin/outbox/{id}/retry` | 管理员 |
| 管理员 | GET | `/api/admin/notification-deliveries/dead` | 管理员 |
| 管理员 | POST | `/api/admin/notification-deliveries/{id}/retry` | 管理员 |

响应体均使用通用的 `Result<T>` 格式：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

分页响应的 `data` 属性包含 `PageResult<T>` 结构：

```json
{
  "records": [],
  "total": 0,
  "current": 1,
  "size": 10,
  "pages": 0
}
```

## 价格提供商 (Price Providers)

`MockPriceProvider` 由 Spring 组件扫描自动加载，支持任何非空商品。它会在本地生成模拟价格，适用于开发、测试以及不涉及真实外部网络价格源的验收流程。

`SerpApiPriceProvider` 专为亚马逊风格的商品实现，拥有更高的提供商优先级，但仅在满足如下配置时支持相关商品：

```yaml
price-provider:
  serpapi:
    enabled: true
    api-key: <从环境变量获取的真实 key>
```

默认情况下已禁用该提供商。请勿向仓库提交真实的 API Key。

## 事务性发件箱与交付 (Transactional Outbox and Delivery)

价格警报意图会以事务形式保存到业务库中的 `tb_outbox_event` 表中。随后 `OutboxRelay` 认领具有租约的就绪数据行，并将消息发布到 RabbitMQ。发布成功后该行将被标记为 `SENT`；可重试的失败标记为 `FAILED_RETRYABLE`；若重试耗尽或出现不可恢复的失败，则标记为 `DEAD`。

`NotificationServiceImpl` 在消费 RabbitMQ 消息后创建应用内通知。如果启用了 Webhook，还会插入一条 `tb_notification_delivery` 记录。`NotificationDeliveryRelay` 负责认领交付任务，将 HTTP POST 请求发送到配置的 Webhook URL；在配置了密钥时使用 `X-Price-Tracker-Signature` 对 Payload 进行签名，并将状态相应地标记为 `SENT`、`FAILED_RETRYABLE` 或 `DEAD`。

管理员恢复端点：

```http
GET  /api/admin/outbox/dead?limit=50
POST /api/admin/outbox/{id}/retry
GET  /api/admin/notification-deliveries/dead?limit=50
POST /api/admin/notification-deliveries/{id}/retry
```

## Redis 使用场景

Redis 主要用于：

- 商品详情与价格的缓存
- 监听列表的缓存
- 缓存重建锁 (分布式锁)
- API 限流控制
- 生产者与消费者幂等性 Key
- JWT 登出的 Token 黑名单 Key：`price-tracker:jwt:blacklist:{jti}`

注意：Redis 数据仅作为派生的运行状态，并非数据真实之源。

## 可观测性

默认 Profile 仅暴露健康状态：

```http
GET /actuator/health
```

`prod` Profile 额外暴露：

```http
GET /actuator/health
GET /actuator/prometheus
```

关键指标名称包括：

- `price_refresh_total`
- `price_refresh_final_total`
- `price_provider_fetch_seconds`
- `price_provider_failure_total`
- `price_alert_publish_total`
- `price_alert_consume_total`
- `outbox_relay_total`
- `notification_delivery_total`
- `rate_limit_block_total`

应用程序能提供 Prometheus 格式 of 指标，但本仓库并不包含生产环境下的 Prometheus 抓取配置、Grafana 仪表盘或报警规则。

## 生产 Profile 配置防线

当启用 `prod` 时，`ProductionConfigValidator` 会进行如下强制性要求校验：

- 必须配置自定义的、长度至少为 32 字符的 JWT 密钥
- `spring.datasource.password` 不能留空
- `spring.rabbitmq.password` 不能留空

该验证器是应用启动时的保护屏障，并非一个完整的密钥管理平台。

## 测试命令

单元测试：

```powershell
.\mvnw.cmd test
```

集成测试：

```powershell
.\mvnw.cmd verify -Pintegration-test
```

集成测试在适用时使用了 Testcontainers，可能需要运行中的 Docker 运行环境支持。

可选的包打包与 Docker 镜像构建：

```powershell
.\mvnw.cmd -DskipTests package
docker build -t price-tracker:local .
```

## 关联文档

- [docs/DOCS_INDEX.md](docs/DOCS_INDEX.md)
- [docs/RELEASE_CANDIDATE_ACCEPTANCE.md](docs/RELEASE_CANDIDATE_ACCEPTANCE.md)
- [docs/API_CONTRACT.md](docs/API_CONTRACT.md)
- [docs/RUNBOOK.md](docs/RUNBOOK.md)
- [docs/PERFORMANCE_BASELINE.md](docs/PERFORMANCE_BASELINE.md)
- [docs/production_readiness_analysis.md](docs/production_readiness_analysis.md)
