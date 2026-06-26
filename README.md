# Price Tracker

Price Tracker 是一个基于 Spring Boot 3 的商品价格跟踪后端。管理员维护商品并触发手动价格刷新，普通用户关注商品并设置目标价；系统当前通过 `MockPriceProvider` 刷新模拟价格，记录价格历史，并在价格达到目标时经 RabbitMQ 异步生成站内通知。

项目包名为 `com.example.price_tracker`。当前形态是单体 REST API 项目，重点展示 MySQL 持久化、Redis 缓存与协调、RabbitMQ 异步通知、JWT 鉴权、TraceId 和可验收交付能力。

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 运行时 | Java 17、Spring Boot 3.3.4 |
| Web | Spring MVC、Validation、AOP |
| 数据访问 | MySQL 8、MyBatis-Plus 3.5.7 |
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
| `tb_notification` | 站内通知 | `is_read`、`send_status`、`sent_at`；当前无通知唯一约束 |

建表文件位于 `src/main/resources/sql/`。Docker Compose 实际挂载其中的 `tb_user.sql`，新数据库会直接创建 `role VARCHAR(20) NOT NULL DEFAULT 'USER'`。Docker 仅在首次创建 MySQL volume 时自动执行这些 SQL；项目当前未引入 Flyway。

已有数据库不会自动增加字段。升级旧 volume 时，确认 `tb_user` 尚无 `role` 后手工执行：

```sql
ALTER TABLE tb_user
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' AFTER nickname,
    ADD CONSTRAINT chk_tb_user_role CHECK (role IN ('USER', 'ADMIN'));
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

`docker-compose.yml` 只启动 MySQL、Redis 和 RabbitMQ，没有应用镜像。应用的数据源目前在 `application.yml` 中固定使用本机 `root/123456`，与容器 root 密码默认值一致；`.env` 中 `MYSQL_USER` 是容器额外创建的普通用户，不是应用当前使用的账号。

```powershell
$env:REDIS_HOST="127.0.0.1"
$env:RABBITMQ_HOST="127.0.0.1"
./mvnw.cmd spring-boot:run
```

Linux/macOS 将 `./mvnw.cmd` 替换为 `./mvnw`，环境变量使用 shell 的 `export` 或命令前缀。完整可复制验收流程见 [docs/LOCAL_RUN_AND_ACCEPTANCE.md](docs/LOCAL_RUN_AND_ACCEPTANCE.md)。

## API 文档与健康检查

- Knife4j：<http://localhost:8080/doc.html>
- OpenAPI JSON：<http://localhost:8080/v3/api-docs>
- Actuator：<http://localhost:8080/actuator/health>

PowerShell 健康检查：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health | ConvertTo-Json -Depth 10
```

预期总状态为 `UP`，并看到 `db`、`redis` 和 `rabbit` 组件为 `UP`。

## 测试命令

```powershell
./mvnw.cmd -q -DskipTests compile
./mvnw.cmd -q test
```

Linux/macOS：

```bash
./mvnw -q -DskipTests compile
./mvnw -q test
```

## 当前能力边界

- 当前价格来源是最低优先级的 `MockPriceProvider` 兼容性兜底；`PriceProvider` 扩展点已经实现，但真实电商 API、Amazon、SerpApi 和爬虫均未接入。
- 当前通知方式只包含站内通知落库与已读状态；邮件、短信、Webhook 和 Push 当前未实现。
- 当前没有事务外盒（Transactional Outbox）、本地消息表或数据库与 MQ 的原子提交；数据库事务成功但发送失败、或发送成功后数据库事务回滚时，存在一致性窗口。
- 当前 RabbitMQ 已有监听器重试、DLX/DLQ、Producer/Consumer Redis 幂等和 `last_notified_price` 业务防重，但这些机制都是有 TTL 或有失败窗口的，不保证 exactly-once；系统语义应按至少一次投递下的尽力去重理解。
- 当前没有配置 publisher confirm/return，Producer 的“发送成功”日志只表示客户端调用未抛异常，不等价于 Broker 已持久化并可消费。
- 当前不是微服务项目，而是单体后端；没有服务注册发现、网关、配置中心或分布式事务。
- 当前有最小 `USER/ADMIN` 角色边界和少量管理员接口，但没有复杂 RBAC、菜单权限或后台前端。
- 当前使用手工 SQL 初始化，没有 Flyway；Docker 初始化脚本只在空 volume 首次启动时执行。

## 文档

统一入口见 [docs/DOCS_INDEX.md](docs/DOCS_INDEX.md)。

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
