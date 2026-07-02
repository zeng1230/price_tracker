# Price Tracker

Price Tracker 是一个基于 Spring Boot 3 的商品价格跟踪后端。管理员维护商品并触发手动价格刷新，普通用户关注商品并设置目标价；系统当前通过 `MockPriceProvider` 刷新模拟价格，记录价格历史，并在价格达到目标时经 RabbitMQ 异步生成站内通知。

项目包名为 `com.example.price_tracker`。当前形态是单体 REST API 项目，重点展示 MySQL 持久化、Redis 缓存与协调、RabbitMQ 异步通知、JWT 鉴权、TraceId 和可验收交付能力。

## 工程可信度保障 (Engineering Trust)

项目通过引入一系列工程化工具和设计模式，成功把代码质量和架构规范提升到了“生产可信”标准：
* **持续集成与覆盖率度量 (CI & JaCoCo)**：接入了 GitHub Actions 持续集成工作流，通过 JaCoCo 进行代码覆盖率度量，保障每一次代码修改的质量。
* **集成测试容器化 (MySQL/Redis/RabbitMQ Testcontainers)**：使用 Testcontainers 进行容器化集成测试隔离。在运行 `mvnw verify` 时自动按需拉取和构建数据库与中间件实例，确保测试的可复现性与真实环境一致。
* **API 契约补强与自动文档 (OpenAPI & Knife4j)**：细化并锁定了核心 API 的统一响应码、错误码和 DTO 字段规范，使用 Knife4j 和 OpenAPI 3 自动渲染规范文档。
* **微观运行时可观测性 (Actuator & Micrometer)**：通过 Spring Boot Actuator 仅对内安全暴露核心运行时监控指标；封装 `PriceTrackerMetrics` 收集业务微观指标（刷新尝试/最终成功率、MQ发布/消费状态、限流阻断拦截等）。
* **清晰可量化的设计边界 (Reliability Boundary Doc)**：编写了详细的可靠性分析文档，不夸大中间件语义，真实定义了当前单体事务下的状态窗口和数据丢失风险，为后续 Outbox 架构升级做好了设计准备。

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 运行时 | Java 17、Spring Boot 3.3.4 |
| Web | Spring MVC、Validation、AOP |
| 数据访问 | MySQL 8、MyBatis-Plus 3.5.7、Flyway |
| 缓存与协调 | Redis 7、Spring Data Redis |
| 消息队列 | RabbitMQ 3 Management、Spring AMQP |
| 鉴权 | JWT、BCrypt |
| 文档与观测 | Knife4j 4.5.0、Actuator、SLF4J MDC TraceId |
| 测试 | JUnit 5、Mockito、Spring Boot Test |

## 核心业务链路

```text
ADMIN 登录 -> 创建或管理商品
USER 登录 -> 添加关注并设置目标价
-> ADMIN 手动刷新或定时任务刷新 MockPriceProvider 价格 -> 更新商品并写入价格历史
-> 当前价 <= 目标价 -> Producer 幂等判断 -> RabbitMQ
-> Consumer 重试、幂等和业务校验 -> 写入站内通知 -> USER 查询/标记已读
```

定时任务默认每 30 分钟扫描有效商品，分页大小默认为 100；单商品刷新失败最多额外重试 2 次，并与其他商品隔离。推荐的管理员手动刷新入口是 `POST /api/admin/products/{productId}/refresh-price`。旧的 `POST /api/internal/products/{id}/refresh-price` 保留用于内部验收，但同样要求 ADMIN 角色。

## 用户角色与权限

当前只支持两类角色，数据库和 JWT 中统一使用大写值：

| 角色 | 能力 |
| --- | --- |
| `USER` | 查询商品和价格历史；新增、查询、修改、取消自己的关注；查询和标记自己的通知 |
| `ADMIN` | 包含普通查询能力，并可查询用户、管理商品、调整商品状态和手动刷新价格 |

普通注册固定创建 `USER`，注册请求不接受 `role`。项目不提供公开管理员注册接口。管理员初始化采用低侵入方式：

```powershell
# 先调用 POST /api/auth/register 注册 admin，再执行：
docker exec price-tracker-mysql mysql -uroot -p123456 price_tracker `
  -e "UPDATE tb_user SET role='ADMIN' WHERE username='admin';"
```

角色变更后必须重新登录。升级前签发的旧 token 不含 `role` claim，会按无效 token 返回 `UNAUTHORIZED`；升级后所有用户都需要重新登录获取新 token。非法角色不会静默降级为 `USER`。

管理员接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/users` | 分页查询用户及状态 |
| GET | `/api/admin/products` | 分页查询全部商品，包括停用商品 |
| PUT | `/api/admin/products/{productId}/status` | 使用 `{"status":0}` 或 `{"status":1}` 停用/启用商品 |
| POST | `/api/admin/products/{productId}/refresh-price` | 复用现有 PriceService/PriceProvider 链路刷新价格 |

现有 `POST /api/products`、`PUT /api/products/{id}`、`DELETE /api/products/{id}` 也仅允许 ADMIN。未登录或 token 无效返回 `UNAUTHORIZED`；已登录 USER 访问管理员能力返回 `FORBIDDEN`。响应继续使用统一 `Result` 结构。

本轮没有用户状态更新接口，因此不存在管理员禁用自己的入口；若未来新增该接口，必须禁止当前管理员禁用自己。当前没有角色表、权限表、菜单权限、复杂 RBAC 或后台前端。

## 系统架构

```text
HTTP Client
  -> Controller / JWT Interceptor / TraceIdFilter
  -> Service
     -> PriceProviderRouter -> PriceProvider -> PriceQuote
     -> MyBatis-Plus -> MySQL
     -> Redis cache / lock / rate limit / idempotency
     -> PriceAlertProducer -> price.alert.exchange
        -> price.alert.queue -> PriceAlertConsumer -> tb_notification
        -> retry exhausted -> price.alert.dlx -> price.alert.dlq
```

应用是一个进程、一个代码库和一个数据库边界；RabbitMQ 用于单体内部的异步解耦，不代表项目已经拆分为微服务。

## 价格数据源

项目通过 `PriceProvider` 扩展点隔离价格来源，Provider 只返回不可变 `PriceQuote`，不直接写数据库、不发送 MQ、不操作 Redis。`PriceProviderRouter` 按 Spring order 升序、再按 `providerCode` 字典序稳定选择实现；未来真实 Provider 使用更高优先级显式覆盖默认实现。

当前默认来源是 `MockPriceProvider`。它复用 `PriceMockUtil` 生成随机模拟价格，支持所有非空商品，并以最低优先级作为本地开发、测试和历史 platform 数据的兼容性兜底。它不代表真实价格来源，也不会访问网络。正常本地开发环境一般不会出现 `PRICE_PROVIDER_NOT_FOUND`；该错误主要用于 Mock 被禁用、测试环境无候选 Provider，或未来真实 Provider 严格匹配商品的场景。

当前没有接入真实 Amazon、SerpApi 或任何爬虫。未来真实 Provider 应放在适配层，并通过配置显式启用；外部调用失败时应分别处理超时、限流、无效数据和商品不存在，不能静默转换成 Mock 报价。

## MySQL 核心表

| 表 | 作用 | 关键字段或约束 |
| --- | --- | --- |
| `tb_user` | 用户与登录身份 | `username` 唯一，密码存 BCrypt 摘要，`role` 仅允许应用写入 `USER/ADMIN` |
| `tb_product` | 商品主数据与当前价格 | `current_price`、`last_checked_at`、`status` |
| `tb_price_history` | 每次有效价格变化 | `old_price`、`new_price`、`captured_at`、`source=MOCK` |
| `tb_watchlist` | 用户关注、目标价与通知开关 | `(user_id, product_id)` 唯一，`last_notified_price` 业务防重 |
| `tb_notification` | 站内通知 | `event_key` 业务唯一键、`is_read`、`send_status`、`sent_at` |

数据库 schema 由 Flyway 管理，migration 位于 `src/main/resources/db/migration/`。推荐使用 MySQL 8.x；当前 `tb_user.role` 使用 MySQL 8 支持的 `CHECK` 约束，不为低版本 MySQL 降级 schema 语义。

`src/main/resources/sql/` 仅作为 legacy/reference 保留，不再由 Docker Compose 挂载建表。Docker Compose 只启动 MySQL 并通过 `MYSQL_DATABASE` 创建空 database；业务表在 Spring Boot 应用启动时由 Flyway 自动创建。只启动 MySQL、不启动应用时，`price_tracker` 库中不会有业务表。

旧库升级必须先确认真实结构。只有已经人工核验达到当前最终 schema 的旧库，才允许使用 `baselineVersion=4` baseline；缺少 `role`、`event_key` 或查询索引的旧库不能直接 baseline 到 4。本地开发库建议备份后重建；必须保留数据时，先手工补齐并核验，再 baseline。

本地旧库一次性 baseline 示例：

```powershell
./mvnw.cmd spring-boot:run `
  -Dspring-boot.run.arguments="--spring.flyway.baseline-on-migrate=true --spring.flyway.baseline-version=4"
```

不要把 `baseline-on-migrate=true` 写入默认配置。

查看 Flyway 执行历史：

```powershell
docker exec price-tracker-mysql mysql -uroot -p123456 price_tracker `
  -e "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

随后按需将管理员账号更新为 `ADMIN`，并让所有用户重新登录。

## Redis 使用点

| 场景 | Key |
| --- | --- |
| 商品详情缓存 | `price-tracker:cache:product:detail:{productId}` |
| 商品价格缓存 | `price-tracker:cache:product:price:{productId}` |
| 用户关注列表缓存 | `price-tracker:cache:user:watchlist:{userId}` |
| 商品不存在的空值缓存 | `price-tracker:cache:null:product:{detail|price}:{productId}` |
| 缓存重建锁 | `price-tracker:lock:product:{detail|price}:{productId}` |
| 用户接口限流 | `price-tracker:rate-limit:{userId}:{apiPath}` |
| Producer 通知幂等 | `price-tracker:idempotent:notify:{userId}:{productId}:{targetPrice}` |
| Consumer 消息幂等 | `price-tracker:idempotent:notify:mq:{messageId}` |

详情和价格缓存基础 TTL 为 30 分钟并增加最多 5 分钟随机抖动；关注列表基础 TTL 为 10 分钟并增加最多 2 分钟抖动；空值缓存为 2 分钟；锁为 10 秒。完整验收见 [docs/REDIS_ACCEPTANCE.md](docs/REDIS_ACCEPTANCE.md)。

## RabbitMQ 通知链路

| 项 | 值 |
| --- | --- |
| 主 Exchange | `price.alert.exchange`（durable direct） |
| 主 Queue | `price.alert.queue`（durable） |
| Routing key | `price.alert` |
| DLX | `price.alert.dlx`（durable direct） |
| DLQ | `price.alert.dlq`（durable） |
| DLQ routing key | `price.alert.dlq` |
| 消费重试 | 默认最多 3 次，间隔 1s，倍数 2，最大 10s |

HTTP `X-Trace-Id` 会进入 MDC，Producer 将其写入 MQ header，Consumer 再恢复到 MDC。Consumer 失败时删除本次幂等 key 并抛出异常，使 Spring AMQP 执行重试；重试耗尽且不重新入队后，消息由主队列的 DLX 参数路由至 DLQ。详见 [docs/RABBITMQ_ACCEPTANCE.md](docs/RABBITMQ_ACCEPTANCE.md)。

## 本地启动

前置条件：JDK 17、Docker Desktop 或兼容的 Docker Compose。

```powershell
Copy-Item .env.example .env
docker compose up -d
docker compose ps
```

`.env.example` 是可提交的本地模板，只包含占位或非敏感配置。复制后按本机环境填写真实密码、账号或私有配置；真实 `.env` 不要提交。

`docker-compose.yml` 只启动 MySQL、Redis 和 RabbitMQ，没有应用镜像。应用的数据源目前在 `application.yml` 中固定使用本机 `root/123456`，与容器 root 密码默认值一致；`.env` 中 `MYSQL_USER` 是容器额外创建的普通用户，不是应用当前使用的账号。
Docker Compose 不再挂载业务建表 SQL。新库的业务表和索引会在执行 `./mvnw.cmd spring-boot:run` 后由 Flyway 创建。

```powershell
$env:REDIS_HOST="127.0.0.1"
$env:RABBITMQ_HOST="127.0.0.1"
./mvnw.cmd spring-boot:run
```

Linux/macOS 将 `./mvnw.cmd` 替换为 `./mvnw`，环境变量使用 shell 的 `export` 或命令前缀。完整可复制验收流程见 [docs/LOCAL_RUN_AND_ACCEPTANCE.md](docs/LOCAL_RUN_AND_ACCEPTANCE.md)。

## API 契约与文档

### 1. 文档访问入口
应用启动后，可以通过以下路径访问在线或结构化文档：
- **Knife4j 在线 API 双语文档**：<http://localhost:8080/doc.html>
- **OpenAPI 3.0 v3 规范 JSON**：<http://localhost:8080/v3/api-docs>
- **Actuator 系统健康指标**：<http://localhost:8080/actuator/health>

PowerShell 查看 Actuator 健康状况：
```powershell
Invoke-RestMethod http://localhost:8080/actuator/health | ConvertTo-Json -Depth 10
```

### 2. 接口核心分类
根据系统当前实现，核心 API 分为以下 5 大类别：
1. **Authentication (用户鉴权)**:
   - 包含用户注册 (`POST /api/auth/register`) 与登录 (`POST /api/auth/login`)。此分类下接口为**公开接口**，无需 JWT Token。
2. **Administration (系统管理)**:
   - 包含用户分页 (`GET /api/admin/users`)、商品分页 (`GET /api/admin/products`)、状态修改 (`PUT /api/admin/products/{productId}/status`) 及价格强制刷新 (`POST /api/admin/products/{productId}/refresh-price`)。此分类下接口要求 **JWT Token** 且用户角色必须为 **ADMIN**。
3. **Product Management (商品管理)**:
   - 普通查询包含详情查询、价格查询、商品列表分页、价格历史分页及趋势查询。
   - 管理员写操作包含添加商品 (`POST /api/products`)、更新商品 (`PUT /api/products/{id}`)、删除商品 (`DELETE /api/products/{id}`)。写操作要求 **JWT Token** 且用户角色必须为 **ADMIN**。
4. **Watchlist Management (关注管理)**:
   - 包含添加关注、修改目标价/配置、取消关注及查询我的关注列表。所有接口均要求 **JWT Token**，部分写操作具备 Redis 分布式限流保护（触发频次受限时响应 429）。
5. **Notification Management (通知管理)**:
   - 包含查询我的通知列表 (`GET /api/notifications/my`) 及标记单条通知已读 (`PUT /api/notifications/{id}/read`)。所有接口要求 **JWT Token**。

### 3. 统一响应格式说明
应用接口采用统一的 `Result<T>` 结构包装返回：
```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

#### 常见状态码与错误类型映射：
- **`200` (SUCCESS)**: 接口请求处理成功。
- **`400` (BAD_REQUEST)**: 客户端传入格式错误或缺少必填基础参数。
- **`401` (UNAUTHORIZED)**: `Authorization` 请求头缺失或 JWT 签名/过期校验未通过。
- **`403` (FORBIDDEN)**: 用户已成功登录，但当前角色（如 `USER`）无权访问需要 `ADMIN` 角色的管理端接口。
- **`422` (VALIDATE_ERROR)**: DTO 数据校验校验失败（如 `@NotBlank` 或数额最小值超限）。
- **`429` (TOO_MANY_REQUESTS)**: 触发了 API 频次限流保护（由 Redis 记录和拦截）。
- **`1001` (PRICE_PROVIDER_NOT_FOUND)**: 未找到合适的第三方报价提供者（Mock 报价源除外）。
- **`1002` (PRICE_NOT_AVAILABLE)**: 聚合商品报价趋势时，目标商品的当前价为空。
- **`500` (SYSTEM_ERROR)**: 服务器内部未知异常。

---

## RabbitMQ 消息事件契约

系统在价格达到用户关注阈值时，会向 RabbitMQ 投递 `PriceAlertMessage` 事件。

### 1. 核心事件字段规范
- **`messageId`**: 消息全局唯一标识符（UUID），主要在 Consumer 消费端作为 Redis 幂等去重 key。
- **`eventKey`**: 业务唯一键，格式为 `TARGET_PRICE_REACHED:userId:productId:watchlistId:targetPrice:currentPrice:triggeredAtEpochMillis`。用于实现数据库级别的唯一键去重。
- **`eventVersion`**: 契约版本号，当前版本固定为 `1`。
- **`userId`**: 被触发关注的用户 ID。
- **`productId`**: 价格变动的商品 ID。
- **`watchlistId`**: 关联的关注项 ID。
- **`currentPrice`**: 触发事件时的当前商品价格。
- **`targetPrice`**: 用户设置的触发报警的目标价格。
- **`productName`**: 商品名称。
- **`triggeredAt`**: 事件触发的时间戳。

### 2. 幂等与去重设计说明
1. **短期快速去重 (Redis)**：
   - 消费端（`PriceAlertConsumer`）在执行核心逻辑前，会在 Redis 中尝试写入 `price-tracker:idempotent:notify:mq:{messageId}`，TTL 默认为 30 分钟。
   - 若写入失败，说明 30 分钟内处理过该 `messageId`，消息会被**直接 ACK 并丢弃**，实现快速排重。
2. **长期强一致去重 (MySQL Unique Key)**：
   - 数据库表 `tb_notification` 的 `event_key` 列建立了唯一索引 `ux_notification_event_key`。
   - 若 Redis 缓存防重 key 提前失效或丢失，数据落库时会通过 SELECT 查询先做防重判断；如因高并发多线程导致 SELECT 判断穿透，MySQL 的唯一约束在执行 INSERT 时会抛出 `DuplicateKeyException`。
   - `NotificationService` 捕获该异常后，会记录日志并**优雅返回**（消费端正常 ACK），确保不会因高并发投递引发重复通知。
3. **一致性边界**：
   - 本系统没有实现事务外盒模式（Transactional Outbox），发送端存在业务事务回滚但消息已投递，或消息发送失败但业务已提交的可能。系统在 RabbitMQ 层面提供的是 **At Least Once** (至少一次) 投递，并依靠消费端的 Redis + DB 强去重达成最终一致性语义。

## 监控与可观测性 (Actuator + Micrometer)

系统引入了 Spring Boot Actuator 与 Micrometer Facade，用于在本地观察系统性能和面试展示系统可观测性。当前阶段暂未接入 Prometheus / Grafana / OpenTelemetry。

### 1. Actuator 端点暴露策略

* **默认环境 (Default Profile)**：仅暴露 `/actuator/health` 以满足基础监控诉求，保持克制，杜绝泄露敏感的 `/actuator/env`, `/actuator/beans`, `/actuator/threaddump` 等端点。
* **开发环境 (dev / local Profile)**：`application-dev.yml` 与 `application-local.yml` 额外暴露 `/actuator/metrics` 端口，用于本地观测性能指标及验证埋点。

### 2. 最小业务指标清单

系统通过 `PriceTrackerMetrics` facade 统一封装埋点逻辑，采用低基数标签防止指标爆炸，禁止包含 `productId`, `userId`, `traceId`, `eventKey` 等高基数变量。

| 指标名称 | 类型 | 含义 | 维度/标签 (Labels) |
| --- | --- | --- | --- |
| `price_refresh_total` | Counter | 价格刷新**尝试**次数 (Attempt-level，而非最终业务次数。若单商品重试2次后成功，会累计2次 `failed` 与1次 `success`) | `result` (success/failed), `provider` (如 MOCK) |
| `price_refresh_final_total` | Counter | 商品价格刷新操作**最终**业务结果次数 (一轮刷新中无论重试几次，最终只记录一次结果) | `result` (success/failed), `provider` (如 MOCK) |
| `price_provider_fetch_seconds` | Timer | 第三方报价源 (PriceProvider) 调用的响应耗时 | `provider` (如 MOCK), `result` (success/failed) |
| `price_alert_publish_total` | Counter | 价格报警消息的发布次数 | `result` (success: 确认接受/failed: 发布异常/returned: 无法路由) |
| `price_alert_consume_total` | Counter | 价格报警消息消费端处理次数 | `result` (success: 通知成功/failed: 消费异常/duplicate: 幂等命中跳过) |
| `rate_limit_block_total` | Counter | 接口触发防刷限流的拦截次数 | `api` (低基数映射，如 watchlist_add, watchlist_update, watchlist_delete) |

### 3. 本地观测方法

在开发环境下启动应用后，可通过如下接口查看指标：
* **查询所有可用指标列表**：`GET http://localhost:8080/actuator/metrics`
* **查看特定指标的详细数据及标签过滤**：`GET http://localhost:8080/actuator/metrics/{metricName}` (例如 `/actuator/metrics/price_refresh_total`)

## 测试与持续集成

### 本地测试与报告

项目包含**单元测试**（Unit Tests）与**集成测试**（Integration Tests），并集成 JaCoCo 用于生成测试覆盖率报告（不设硬性覆盖率门禁）：
- **报告路径**：`target/site/jacoco/index.html`

#### 1. 单元测试 (Unit Tests)
单元测试仅涉及基础的业务逻辑，不依赖任何外部 MySQL、Redis、RabbitMQ 或 Docker 容器。`test` profile 会禁用 Flyway、调度任务、RabbitMQ listener 自动启动和 RabbitMQ health check，避免 `.\mvnw.cmd test` 连接本机中间件。
- **Windows 环境**：
  ```powershell
  # 编译项目（跳过测试）
  .\mvnw.cmd -q -DskipTests compile
  # 执行单元测试并生成 JaCoCo 报告
  .\mvnw.cmd test
  ```
- **Linux / macOS 环境**：
  ```bash
  # 编译项目（跳过测试）
  ./mvnw -q -DskipTests compile
  # 执行单元测试并生成 JaCoCo 报告
  ./mvnw test
  ```

#### 2. 集成测试 (Integration Tests)
集成测试基于 **Testcontainers**，执行时会在本地启动临时的 MySQL 8、RabbitMQ 和 Redis 容器以校验系统的核心组件与高可用逻辑。
* **隔离策略**：
  * `.\mvnw.cmd test` 仅跑本地**单元测试**，不连接本机 MySQL、Redis、RabbitMQ，也不启动容器。
  * `.\mvnw.cmd verify -Pintegration-test` 在 `verify` 阶段运行以 `IT` 结尾的**集成测试**，这会根据测试类需要动态拉取并启动 MySQL、RabbitMQ 和 Redis 容器。
* **前提条件**：本地环境必须安装并运行 Docker (如 Docker Desktop 或 Rancher Desktop)。
* **API 版本兼容注意事项**：若本地 Docker Desktop 版本较新导致执行时抛出 `BadRequestException (Status 400)`，可在当前用户家目录下 (如 `C:\Users\<Username>`) 创建 `.docker-java.properties` 文件并写入 `api.version=1.44`（切记不要将此文件提交到项目仓库）。

##### 集成测试覆盖点：
* **MySQL + Flyway 校验 (`MySQLFlywayIT.java`)**：
  * 验证 Flyway 迁移脚本全部执行成功。
  * 验证 `tb_user`, `tb_product`, `tb_price_history`, `tb_watchlist`, `tb_notification` 等核心业务表和唯一键/联合索引均已正确创建。
* **RabbitMQ 通知链路校验 (`RabbitMQNotificationIT.java`)**：
  * **绑定关系**：校验 Exchange, Queue, Routing key, 以及 DLQ/DLX 绑定关系在 Broker 真实存在。
  * **正常投递**：验证 `PriceAlertMessage` 的发布与消费，并确认最终成功持久化到 `tb_notification` 且更新了 `tb_watchlist` 的最近通知价。
  * **缓存去重**：验证高并发下由 Redis 幂等 Key 实现的消息防重逻辑（第二次消息被消费端丢弃）。
  * **唯一键降级防重**：验证在 Redis 幂等 Key 丢失时，数据库的 `ux_notification_event_key` 唯一索引能作为兜底手段拒绝重复消息，即使 MyBatis select 返回 null 触发插入也会捕获 `DuplicateKeyException` 优雅忽略，保证数据最终一致性。
  * **死信队列 (DLQ)**：通过 Stub 注入模拟消费端异常，验证消费失败后，消息触发 Listener 重试（配置为 2 次以加速测试），并在重试耗尽后自动转移至死信队列 (`price.alert.dlq`)。
* **Redis 行为校验 (`RedisBehaviorIT.java`)**：
  * **轻量级上下文**：本测试采用 Spring Boot 局部切片加载，仅加载 Redis 基础配置与操作类，避免了非必要的 MySQL 或 RabbitMQ 容器启动，保持极高的运行速度。
  * **基本读写**：验证 Redis Cache 服务的 Key-Value 正常存取及删除。
  * **限流 TTL**：验证限流 Key 正常递增且具备正向 TTL（在限制窗口秒数内递减消亡）。
  * **防重/幂等 TTL**：验证幂等 Key 在生存周期内可以正常防重并有预期的正向 TTL（防重失效后正常消亡）。

* **Windows 环境**：
  ```powershell
  # 执行单元测试与全部集成测试（会按需启动 MySQL, RabbitMQ, Redis 容器）
  .\mvnw.cmd verify -Pintegration-test
  ```
* **Linux / macOS 环境**：
  ```bash
  # 执行单元测试与全部集成测试
  ./mvnw verify -Pintegration-test
  ```

### CI 持续集成

GitHub Actions 持续集成配置位于 `.github/workflows/ci.yml`。每次推送到分支或提交 Pull Request 时会自动触发 CI。
* **当前 CI Job**：当前 CI 阶段默认仅执行单元测试 (`./mvnw test`)，并在构建前使用 `docker compose config --quiet` 校验 Docker 配置文件语法。集成测试 (`./mvnw verify -Pintegration-test`) 可在后续作为独立 CI 流程接入。

## 当前能力边界

- 当前价格来源是最低优先级的 `MockPriceProvider` 兼容性兜底；`PriceProvider` 扩展点已经实现，但真实电商 API、Amazon、SerpApi 和爬虫均未接入。
- 当前通知方式只包含站内通知落库与已读状态；邮件、短信、Webhook 和 Push 当前未实现。
- 当前没有事务外盒（Transactional Outbox）、本地消息表或数据库与 MQ 的原子提交；数据库事务成功但发送失败、或发送成功后数据库事务回滚时，存在一致性窗口。
- 当前 RabbitMQ 已有监听器重试、DLX/DLQ、Producer/Consumer Redis 幂等和 `last_notified_price` 业务防重，但这些机制都是有 TTL 或有失败窗口的，不保证 exactly-once；系统语义应按至少一次投递下的尽力去重理解。
- 当前已配置 publisher confirm/return；confirm ack 只表示 exchange 接收，return 表示不可路由且按发送失败处理，仍不等价于 exactly-once。
- 当前不是微服务项目，而是单体后端；没有服务注册发现、网关、配置中心或分布式事务。
- 当前有最小 `USER/ADMIN` 角色边界和少量管理员接口，但没有复杂 RBAC、菜单权限或后台前端。
- 当前使用 Flyway 管理数据库 schema；Docker 只负责启动 MySQL 并创建空 database，不再负责业务建表。


## 价格趋势聚合

已提供登录用户可访问的轻量趋势摘要接口：

```http
GET /api/products/{productId}/price-trend
```

返回当前价、近 7 天最低价、近 30 天最低价、历史最低价、历史最高价、平均价、价格变化次数、当前价相对历史最低价的差值和百分比，以及最近一次价格变动时间。普通 `USER` 和 `ADMIN` 复用同一接口；不存在或停用的商品返回 `NOT_FOUND`，当前价为空时返回 `PRICE_NOT_AVAILABLE`，需要先刷新商品价格。

统计采用价格变动样本口径：

- 平均价样本为第一条历史的 `old_price`、每条历史的 `new_price`，以及必要时追加的 `tb_product.current_price`。
- `current_price` 与最后一条 `new_price` 相同时不重复计入平均价；不同时作为最新样本追加。
- 历史最低价和最高价始终与 `current_price` 比较，以兼容管理员直接修改当前价但没有写入价格历史的情况。
- 近 7 天和近 30 天最低价取窗口内历史记录的 `old_price/new_price` 与当前价的最小值；窗口内没有历史时回退为当前价。
- 上述指标是变动样本近似统计，不是按价格持续时间加权的价格状态计算。

趋势结果当前直接由 MySQL 聚合，不使用 Redis 缓存，也没有新增表或索引。现有 `idx_price_history_product_captured_at(product_id, captured_at)` 同时支持价格历史分页、时间窗口筛选和首末历史定位。

## P1 RabbitMQ Reliability Update

The price alert publisher now enables publisher confirm and publisher return:

- `spring.rabbitmq.publisher-confirm-type=correlated`
- `spring.rabbitmq.publisher-returns=true`
- `spring.rabbitmq.template.mandatory=true`

Publisher confirm ack only means the exchange accepted the message. It does not prove
that the message was routed to `price.alert.queue`. Publisher return means the message
was unroutable and is treated as a business delivery failure even if a later confirm
ack arrives for the same publish operation.

On synchronous publish exceptions, confirm nack, or publisher return, the Producer
deletes the Redis producer idempotency key. This only allows a later price refresh to
publish again; it does not automatically replay the current message.

New `PriceAlertMessage` instances carry a non-empty `eventKey`:

```text
TARGET_PRICE_REACHED:userId:productId:watchlistId:targetPrice:currentPrice:triggeredAtEpochMillis
```

`targetPrice` and `currentPrice` are normalized with `setScale(2, HALF_UP).toPlainString()`.
`triggeredAt` is normalized to UTC epoch milliseconds. `tb_notification.event_key`
is nullable for legacy rows, but new notifications must write a non-empty event key.
The unique index `ux_notification_event_key` provides long-term business idempotency;
the existing Redis keys remain fast TTL-based duplicate suppression.

This project still has no transactional outbox, no `mq_message_log`, and no
exactly-once guarantee. Retry and DLQ only cover messages that reached the main queue
and then failed during consumption.
