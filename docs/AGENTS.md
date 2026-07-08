# AGENTS.md

此仓库是一个 Java/Spring Boot 价格追踪器 (Price Tracker) 后端。更倾向于立足于当前代码做出直接的工程决策，而不是进行宽泛的教学总结。

## 当前项目上下文

- 该项目是一个 Spring Boot 单体应用，而不是微服务系统。
- Java 包根路径为 `com.example.price_tracker`。
- MySQL 是数据真实之源 (Source of truth)。
- Redis 用于缓存、锁、限流、幂等性以及 JWT 登出黑名单。
- RabbitMQ 用于单体应用内部的异步通知传递，并提供最少一次 (at-least-once) 语义。
- Flyway 负责管理 `src/main/resources/db/migration` 下的模式迁移。
- Docker Compose 启动 MySQL、Redis 和 RabbitMQ。它不运行应用程序容器。
- 根目录下存在一个用于打包 Spring Boot jar 的 `Dockerfile`。

## 当前已实现的能力

- 事务性发件箱 (Transactional Outbox) 通过 `tb_outbox_event` 实现。
- 发件箱的认领/租约 (claim/lease) 通过 `claim_owner`、`claimed_at` 和 `claimed_until` 完成。
- Webhook 交付通过 `tb_notification_delivery`、`DefaultWebhookDeliveryClient` 和 `NotificationDeliveryRelay` 完成。
- 存在针对发件箱和通知交付的管理员 DEAD 查询和重试端点。
- 存在 `SerpApiPriceProvider`，但默认禁用。
- `MockPriceProvider` 是本地/测试回退方案，不调用外部服务。
- 已完成 JWT 登出和 Redis 黑名单功能。
- 已完成 `prod` 配置文件的敏感配置校验。
- 通过 Actuator 和 Micrometer 注册表支持 Prometheus 端点能力。

## 成熟度边界

当前项目是一个 release-candidate 骨架，正在逐步接近可验证的发布候选版本 (release candidate)。它仍不能被称为达到了商用生产就绪 (production-ready) 标准。

不要声称系统具备以下能力：

- 高可用部署
- 多区域灾难恢复
- 完整的微服务架构
- Kafka 事件流平台
- 完整的 OpenTelemetry 追踪
- Grafana 生产告警平台
- 多平台真实价格爬取
- 完整的后端/前端仪表盘 (Dashboard)
- SLA 或 SLO 保证

## 下一步优先级

1. 运行并记录 `docs/RELEASE_CANDIDATE_ACCEPTANCE.md` 中的完整端到端 (E2E) 验收路径。
2. 测量 `docs/PERFORMANCE_BASELINE.md` 中的最低性能与容量基线。
3. 验证类似于生产环境的部署行为，包括生产配置防线、`/actuator/prometheus`、Docker 镜像构建、RabbitMQ 中继行为以及 Webhook HMAC 交付。

## 工程原则

- 除非用户明确要求进行独立的架构迁移，否则请保持单体应用边界的清晰性。
- 保持业务事务简短。不要在外部 HTTP、MQ 等待或 Redis 操作期间持有数据库事务。
- 将 RabbitMQ 视为最少一次 (at-least-once)。消费者和下游的副作用必须保持幂等。
- 使用持久的数据库状态进行恢复。日志不是恢复状态。
- 尽可能使用唯一约束来保证所有权 and 幂等性。
- 除非用户明确批准，否则避免引入新的中间件。
- 不要将真实的 API 密钥或密钥添加到仓库中。
- 除非任务明确要求修改模式，否则不要修改迁移文件。

## 文档规则

- 保持 README、API 契约、操作手册 (runbook) 以及发布候选版本验收文档与代码同步。
- 不要将未来的生产就绪声明写成当前已实现的能力。
- If a capability is implemented but not operationally verified, state that it is implemented and still requires acceptance evidence. (如果某项能力已实现但尚未在运行中验证，应说明其已实现，但仍需要验收证据。)
- 如果尚未测量性能数据，请填写 `pending measurement` 或 `待实测`；切勿凭空编造结果。
