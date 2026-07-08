# 发布候选版本验收 (Release Candidate Acceptance)

## 当前版本定位

当前项目状态：**release-candidate 骨架**。它正在逐步演进为**可验证的发布候选版本 (Release Candidate)**，但它目前还不是一个**小规模真实生产服务**。

为什么它更接近可验证的发布候选版本：

- 发件箱 (Outbox) 认领/租约机制通过 `tb_outbox_event` 表的 `claim_owner`、`claimed_at` 和 `claimed_until` 字段实现。
- Webhook 交付通过 `tb_notification_delivery` 和 `NotificationDeliveryRelay` 实现。
- 存在针对 DEAD 状态的发件箱和 Webhook 交付记录的管理员重试端点。
- 存在 `SerpApiPriceProvider` 作为真实价格源原型，但默认禁用。
- JWT 登出使用基于 Redis 的黑名单。
- 实现了 `prod` 配置文件的敏感配置校验。
- 通过 Actuator 和 Micrometer 注册表支持 Prometheus 端点。
- 提供了 Dockerfile。

为什么它目前还不是小规模真实生产服务：

- 尚未完成并记录完整的端到端 (E2E) 验收。
- 尚未完成生产环境部署验证。
- 尚未测量性能和容量基线。
- 此仓库中未配置外部监控、仪表盘和告警。
- 用户层面的通知偏好仅限于 `notifyEnabled` 和目标价格。

## 验收能力清单

| 维度 | 是否完成 | 如何验证 | 残留风险 |
| --- | --- | --- | --- |
| 业务闭环 | 是 | 注册、登录、商品创建、监听列表、价格刷新、价格历史、通知流程已存在。运行本文档中的 E2E 路径，并查询商品历史和用户通知。 | 发布候选版本仍需捕获手动 E2E 凭证。 |
| 真实价格源 | 是 | 存在 `SerpApiPriceProvider`，且启用时支持亚马逊风格的商品。`MockPriceProvider` 仍为默认值。确认默认 `price-provider.serpapi.enabled=false` 并运行提供商测试。 | SerpApi 的行为未经生产流量、限额或脏数据的验证。 |
| 外部通知 | 是 | Webhook 交付表、中继、HTTP 客户端、重试/DEAD 状态和 HMAC 签名均存在。启用 `notification.webhook.enabled=true`，配置模拟端点并观察 `tb_notification_delivery`。 | 缺乏用户自定义的 Webhook 偏好或多通道路由。 |
| 消息可靠性 | 是 | 事务性发件箱 (Outbox)、中继认领/租约、RabbitMQ 发布确认/回退、死信交换机/死信队列 (DLX/DLQ)、重试/DEAD 状态已存在。运行单元/集成测试并在刷新期间检查 `tb_outbox_event`。 | 对于长期保留的 SENT/DEAD 状态数据行，没有归档策略。 |
| 幂等性 | 是 | 存在发件箱事件 Key、通知事件 Key、交付唯一 Key、Redis 生产者/消费者幂等性。重新触发相同的符合条件的报警，验证不会创建重复的数据行。 | 幂等性是基于 Redis TTL 和数据库约束的最佳实践，并非绝对的一次性交付。 |
| 数据库治理 | 是 | Flyway 迁移 V1-V6 定义了表结构、索引、发件箱和交付表。运行 `.\mvnw.cmd verify -Pintegration-test` 并检查 `flyway_schema_history`。 | 尚未在类生产的数据量下测量查询计划基线。 |
| Redis 使用 | 是 | Redis 用于缓存、限流、分布式锁、幂等和 JWT 黑名单。运行 Redis 行为测试和登出黑名单验证。 | Redis 宕机会导致登出、限流、缓存和重复项抑制功能降级。 |
| 安全 | 是 | 存在 JWT 认证、ADMIN 角色防线、BCrypt 密码加密、Redis 黑名单、生产配置验证。使用 USER Token 请求管理员端点，登出后重用 Token，并运行生产验证器测试。 | 缺乏完整的账户生命周期、管理员 UI、密钥轮转流程或审计日志。 |
| 可观测性 | 是 | 提供 Actuator 健康检查和 Prometheus 格式的指标；定义了业务指标。在 `prod` 环境下，在产生流量后请求 `/actuator/prometheus` 并检查预期的指标名称。 | 仓库中没有生产环境的 Prometheus 抓取配置、Grafana 仪表盘或告警规则。 |
| 运维恢复 | 是 | 存在操作手册 (Runbook) 以及管理员 DEAD 状态查询/重试端点。强制制造或检查 DEAD 记录，并调用管理员重试端点。 | 运维流程是手动的，没有仪表盘或报警支持。 |
| 部署 | 是 | 存在 Dockerfile；Compose 可启动 MySQL、Redis、RabbitMQ。运行 `.\mvnw.cmd -DskipTests package` 和 `docker build -t price-tracker:local .`。 | Compose 不运行应用容器，且生产部署尚未得到验证。 |
| 测试 | 是 | 存在针对核心行为的单元测试和集成测试 Profile。运行 `.\mvnw.cmd test` 和 `.\mvnw.cmd verify -Pintegration-test`。 | 缺乏完整的端到端 (E2E) 测试套件或实测的负载基线。 |

## 手动 E2E 验收路径

以下路径使用已实现的端点。请将 Token、ID 和密码替换为您自己环境中的实际值。

1. 启动中间件：

   ```powershell
   Copy-Item .env.example .env
   docker compose up -d
   docker compose ps
   ```

2. 启动应用程序：

   ```powershell
   $env:REDIS_HOST="127.0.0.1"
   $env:RABBITMQ_HOST="127.0.0.1"
   .\mvnw.cmd spring-boot:run
   ```

3. 注册普通用户：

   ```http
   POST /api/auth/register
   Content-Type: application/json

   {
     "username": "user1",
     "password": "secret123",
     "email": "user1@example.com",
     "nickname": "User One"
   }
   ```

4. 注册管理员候选人：

   ```http
   POST /api/auth/register
   Content-Type: application/json

   {
     "username": "admin",
     "password": "secret123",
     "email": "admin@example.com",
     "nickname": "Admin"
   }
   ```

5. 在本地提升管理员权限：

   ```powershell
   docker exec price-tracker-mysql mysql -uroot -p123456 price_tracker `
     -e "UPDATE tb_user SET role='ADMIN' WHERE username='admin';"
   ```

6. 以用户和管理员身份登录：

   ```http
   POST /api/auth/login
   Content-Type: application/json

   {
     "username": "user1",
     "password": "secret123"
   }
   ```

   ```http
   POST /api/auth/login
   Content-Type: application/json

   {
     "username": "admin",
     "password": "secret123"
   }
   ```

7. 使用管理员 Token 创建商品：

   ```http
   POST /api/products
   Authorization: Bearer <adminToken>
   Content-Type: application/json

   {
     "productName": "Acceptance Product",
     "productUrl": "https://example.com/product/acceptance",
     "platform": "amazon",
     "currentPrice": 100.00,
     "currency": "USD",
     "imageUrl": "https://example.com/product.png"
   }
   ```

8. 使用用户 Token 创建监听项：

   ```http
   POST /api/watchlist
   Authorization: Bearer <userToken>
   Content-Type: application/json

   {
     "productId": <productId>,
     "targetPrice": 1000.00,
     "notifyEnabled": 1
   }
   ```

   设置较高的目标价格使默认的模拟价格更容易触发警报。

9. 使用管理员 Token 触发价格刷新：

   ```http
   POST /api/admin/products/<productId>/refresh-price
   Authorization: Bearer <adminToken>
   ```

10. 验证价格历史：

    ```http
    GET /api/products/<productId>/price-history?pageNum=1&pageSize=10
    Authorization: Bearer <userToken>
    ```

11. 验证发件箱 (Outbox) 事件的创建与中继：

    ```sql
    SELECT id, event_key, status, attempts, claim_owner, claimed_until
    FROM tb_outbox_event
    ORDER BY id DESC
    LIMIT 10;
    ```

    预期状态通常是短暂处于 `PENDING`，中继成功后变为 `SENT`。如果 RabbitMQ 发布重复失败，记录可能会转为 `DEAD`。

12. 验证 RabbitMQ 的发布与消费：

    - 在 `http://localhost:15672` 打开 RabbitMQ 管理后台。
    - 检查 `price.alert.exchange`、`price.alert.queue` 和 `price.alert.dlq`。
    - 确认中继后主队列没有异常积压。

13. 验证应用内通知：

    ```http
    GET /api/notifications/my?pageNum=1&pageSize=10
    Authorization: Bearer <userToken>
    ```

14. 标记通知为已读：

    ```http
    PUT /api/notifications/<notificationId>/read
    Authorization: Bearer <userToken>
    ```

15. 验证启用 Webhook 时的交付情况：

    使用以下命令启动应用：

    ```powershell
    $env:WEBHOOK_ENABLED="true"
    $env:WEBHOOK_URL="http://127.0.0.1:9000/hook"
    $env:WEBHOOK_SECRET="local-test-secret"
    ```

    然后重复价格刷新流程并检查：

    ```sql
    SELECT id, event_key, channel, status, attempts, last_error
    FROM tb_notification_delivery
    ORDER BY id DESC
    LIMIT 10;
    ```

    返回 2xx 的模拟端点会使状态变为 `SENT`。诸如 500、408 或 429 的可重试 HTTP 状态码将触发重试，并在尝试次数耗尽后最终转为 `DEAD`。

16. 查询 DEAD 发件箱事件：

    ```http
    GET /api/admin/outbox/dead?limit=50
    Authorization: Bearer <adminToken>
    ```

17. 重试 DEAD 发件箱事件：

    ```http
    POST /api/admin/outbox/<id>/retry
    Authorization: Bearer <adminToken>
    ```

18. 查询 DEAD Webhook 交付任务：

    ```http
    GET /api/admin/notification-deliveries/dead?limit=50
    Authorization: Bearer <adminToken>
    ```

19. 重试 DEAD Webhook 交付任务：

    ```http
    POST /api/admin/notification-deliveries/<id>/retry
    Authorization: Bearer <adminToken>
    ```

20. 登出并验证 JWT 黑名单：

    ```http
    POST /api/auth/logout
    Authorization: Bearer <userToken>
    ```

    重用相同的 Token：

    ```http
    GET /api/notifications/my
    Authorization: Bearer <oldUserToken>
    ```

    预期结果：返回 `UNAUTHORIZED`，因为 Token 已通过 Redis 黑名单被吊销。

## 生产预检清单

| 检查项 | 验证方式 |
| --- | --- |
| 单元测试 | 运行 `.\mvnw.cmd test`。 |
| 集成测试 | 在 Docker 可用的情况下运行 `.\mvnw.cmd verify -Pintegration-test`。 |
| Flyway 架构 | 验证 V1-V6 迁移已应用且 `flyway_schema_history` 报告成功。 |
| 生产敏感配置验证 | 启动时使用 `prod` profile，故意留空或使用默认敏感配置；应用必须快速失败。然后使用非默认值启动。 |
| RabbitMQ Confirm/Return | 运行发布者/中继测试以及手动发布路径；确认无法路由的消息被视为失败。 |
| 发件箱认领并发 | 运行中继测试，对于手动验证，可以针对同一个数据库启动多个应用实例，验证某一行在同一时间只能被一个所有者认领。 |
| Webhook HMAC 签名 | 使用本地模拟端点并验证 `X-Price-Tracker-Signature` 头部是否等于 `sha256=<hmac>`。 |
| JWT 黑名单 | 登出，然后重用旧 Token 访问任何受保护的端点。 |
| Prometheus 端点 | 在 `prod` 模式下请求 `/actuator/prometheus` 并确认 Micrometer 输出。 |
| Docker 镜像构建 | 运行 `.\mvnw.cmd -DskipTests package`，然后运行 `docker build -t price-tracker:local .`。 |

## 不能声称已具备的能力

- 不能声称商用生产就绪。
- 不能声称高可用部署。
- 不能声称多区域灾难恢复。
- 不能声称完整的微服务架构。
- 不能声称 Kafka 事件流平台。
- 不能声称完整的 OpenTelemetry 分布式追踪。
- 不能声称 Grafana 生产告警平台。
- 不能声称多平台真实价格爬取。
- 不能声称完整的后端/前端仪表盘。
- 不能保证 SLA 或 SLO。
