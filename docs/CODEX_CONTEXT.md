# Price Tracker Codex Context

> Snapshot date: 2026-06-01
> Purpose: hand off the current backend project state to the next Codex session.
> Constraint: stop feature development here. The next implementation window should focus only on RabbitMQ reliability hardening.

## 1. Current Project Stage

Price Tracker is a Spring Boot 3 + Java 17 monolithic backend. The basic business loop, Redis concurrency enhancements, RabbitMQ asynchronous notification path, observability basics, and middleware delivery files are present.

The project is currently in the **Integration & Delivery closing stage**. The next recommended development topic is a narrowly scoped **RabbitMQ reliability hardening** window. Do not introduce microservices, frontend work, or broad refactors.

## 2. Completed Capabilities

| Area | Current capability | Main evidence |
| --- | --- | --- |
| Authentication | User registration, login, JWT generation/parsing, current-user context | `AuthController`, `UserController`, `JwtTokenUtil`, `JwtAuthenticationInterceptor` |
| Product | Create, detail query, current-price query, pagination, update, logical delete | `ProductServiceImpl` |
| Watchlist | Add watch, reactivate cancelled watch, list watches, update target price, enable/disable notification, cancel watch | `WatchlistServiceImpl` |
| Price refresh | Manual single-product refresh, scheduled active-product refresh, mock price generation, history persistence, product cache eviction | `PriceServiceImpl`, `PriceRefreshTask`, `PriceMockUtil` |
| Notification | Current-user notification list, mark-read, MQ-consumer notification persistence | `NotificationServiceImpl` |
| Redis | Product detail/price cache, null cache, cache rebuild lock, TTL jitter, watchlist cache, rate limiting, producer-side and consumer-side notification idempotency | `redis/*`, `ProductServiceImpl`, `WatchlistServiceImpl`, `PriceServiceImpl`, `PriceAlertConsumer` |
| RabbitMQ | Durable direct exchange, durable queue, routing binding, JSON message conversion, asynchronous producer and consumer, traceId header propagation | `RabbitMQConfig`, `PriceAlertProducer`, `PriceAlertConsumer` |
| Observability | Global exception handling, structured business logs, HTTP traceId filter, MQ traceId propagation, Actuator health endpoint | `GlobalExceptionHandler`, `TraceIdFilter`, `application.yml` |
| Delivery | Middleware Docker Compose, environment variable example, SQL initialization mounts, SQL index documentation | `docker-compose.yml`, `.env.example`, `src/main/resources/sql/indexes.sql`, `docs/SQL_INDEX_EXPLAIN.md` |

## 3. Recent Delivery Changes

The latest low-risk delivery window completed these files without changing core business logic:

| File | Change |
| --- | --- |
| `docker-compose.yml` | Added MySQL 8, Redis 7, and RabbitMQ Management services with persistent volumes, exposed ports, health checks, and ordered SQL initialization mounts. |
| `.env.example` | Added MySQL, Redis, RabbitMQ, and JWT example environment variables. |
| `.gitignore` | Added `.env` exclusion so local secrets are not committed. |
| `src/main/resources/sql/indexes.sql` | Added query-driven MySQL indexes after table initialization. |
| `docs/SQL_INDEX_EXPLAIN.md` | Documented each added index, corresponding query, code location, and intentionally omitted indexes. |
| `README.md` | Added Docker Compose middleware startup, verification commands, SQL initialization notes, project startup, and test commands. |

The worktree is currently dirty. Before committing, run `git status --short` and review unrelated existing files separately. Do not assume every uncommitted file belongs to the Docker/index window.

## 4. Core Business Flow

```text
Register / login
  -> obtain JWT
  -> create product
  -> add watchlist record and target price
  -> trigger manual refresh or wait for PriceRefreshTask
  -> PriceServiceImpl refreshes product current price
  -> evict product detail and price caches
  -> insert tb_price_history when the price changes
  -> query active and notification-enabled watchlist records
  -> when currentPrice <= targetPrice, acquire producer-side Redis idempotency key
  -> publish PriceAlertMessage to RabbitMQ
  -> PriceAlertConsumer receives message
  -> acquire consumer-side Redis idempotency key
  -> NotificationServiceImpl validates business state
  -> insert tb_notification
  -> update tb_watchlist.last_notified_price
```

Scheduled refresh entry:

- `PriceRefreshTask.refreshActiveProducts()` runs with cron `${price.refresh.cron:0 0/30 * * * ?}`.
- `PriceServiceImpl.refreshActiveProducts()` pages through active products by `status = 1`, ordered by `id ASC`.
- A single product refresh failure is retried up to two times and does not stop the full scan.

Known transaction-boundary limitation:

- `PriceServiceImpl.refreshProductPrice(Long)` is annotated with `@Transactional`.
- The scheduled batch path calls private `refreshProductPriceInternal(Long)` directly, so each scheduled single-product refresh does not pass through the Spring transactional proxy.
- This is not changed in the current snapshot. Treat it as a later targeted consistency task after the RabbitMQ reliability window.

## 5. Current RabbitMQ Notification Path

### Topology

| Item | Current value | Evidence |
| --- | --- | --- |
| Exchange | `price.alert.exchange` | `RabbitMQConfig.PRICE_ALERT_EXCHANGE` |
| Exchange type | durable `DirectExchange` | `RabbitMQConfig.priceAlertExchange()` |
| Queue | durable `price.alert.queue` | `RabbitMQConfig.PRICE_ALERT_QUEUE` |
| Routing key | `price.alert` | `RabbitMQConfig.PRICE_ALERT_ROUTING_KEY` |
| Message body | `PriceAlertMessage` JSON | `PriceAlertMessage`, `Jackson2JsonMessageConverter` |
| Consumer | `@RabbitListener(queues = RabbitMQConfig.PRICE_ALERT_QUEUE)` | `PriceAlertConsumer.consume(...)` |

### Producer behavior

`PriceServiceImpl.sendAlertIfNotDuplicate(...)` writes a Redis idempotency key before calling `PriceAlertProducer.send(...)`. The producer logs the message, publishes it with `RabbitTemplate.convertAndSend(...)`, and copies MDC `traceId` into the `X-Trace-Id` message header when present.

### Consumer behavior

`PriceAlertConsumer.consumeInternal(...)` builds a Redis idempotency key, preferring `messageId` and falling back to business fields. Duplicate messages are logged with `decision=ack_skip`. New messages call `NotificationServiceImpl.consumePriceAlert(...)`, which performs a second business validation and prevents same-price duplicates using `tb_watchlist.last_notified_price`.

### Reliability gaps to address next

- Consumer exceptions are currently caught and logged without rethrowing. The listener returns normally, so a failed notification can be acknowledged and skipped.
- The consumer idempotency key remains until TTL even when business handling fails, which suppresses immediate retry.
- There is no dead-letter exchange, dead-letter queue, or delayed retry queue.
- There is no `mq_message_log` or outbox table for persistent delivery state.
- Producer logs success after `convertAndSend(...)`, but publisher confirm/return handling is not yet configured.
- `tb_notification` does not have a database-level business uniqueness constraint.

The next session should choose a minimal RabbitMQ reliability scope and add tests before implementation.

## 6. Current Redis Usage

All application keys are generated by `RedisKeyManager` with prefix `price-tracker:`.

| Purpose | Key pattern | Used by |
| --- | --- | --- |
| Product detail cache | `price-tracker:cache:product:detail:{productId}` | `ProductServiceImpl.getProductDetail(...)` |
| Product price cache | `price-tracker:cache:product:price:{productId}` | `ProductServiceImpl.getCurrentPrice(...)` |
| User watchlist cache | `price-tracker:cache:user:watchlist:{userId}` | `WatchlistServiceImpl.pageMyWatchlist(...)` |
| Null-value cache | `price-tracker:cache:null:{businessKey}` | `ProductServiceImpl` |
| Cache rebuild lock | `price-tracker:lock:{businessKey}` | `ProductServiceImpl`, `RedisDistributedLock` |
| API rate limit | `price-tracker:rate-limit:{userId}:{apiPath}` | `RedisRateLimitAspect`, `RedisRateLimiter` |
| Notification idempotency | `price-tracker:idempotent:notify:{businessId}` | `PriceServiceImpl`, `PriceAlertConsumer` |

Important boundaries:

- Redis is acceleration and short-term coordination state, not the MySQL source of truth.
- Product update, logical delete, and price refresh evict product detail, price, and null caches.
- Watchlist writes evict the user watchlist cache.
- Watchlist cache keys currently contain only `userId`, while `pageMyWatchlist(...)` accepts pagination parameters. This may return the first cached page for later page requests. It is a known follow-up issue, not part of the RabbitMQ window.

## 7. MySQL Tables and Index Status

### Core tables

| Table | Purpose | Current key constraints |
| --- | --- | --- |
| `tb_user` | User identity and login | Primary key; `username` unique constraint |
| `tb_product` | Product metadata and current price | Primary key |
| `tb_price_history` | Price change history | Primary key |
| `tb_watchlist` | User-product watch rule, target price, last notified price | Primary key; `uk_user_product(user_id, product_id)` |
| `tb_notification` | Persisted in-app price alert notification | Primary key |

The current schema does not include `refresh_task_log`, `mq_message_log`, role tables, or an outbox table.

### Added query-driven indexes

`src/main/resources/sql/indexes.sql` is mounted into the MySQL Docker initialization directory after the table DDL files.

| Index | Table | Covered query |
| --- | --- | --- |
| `idx_notification_user_created_at` | `tb_notification(user_id, created_at)` | Current-user notification pagination |
| `idx_price_history_product_captured_at` | `tb_price_history(product_id, captured_at)` | Product price-history pagination |
| `idx_watchlist_user_status_updated_at` | `tb_watchlist(user_id, status, updated_at)` | Active user watchlist pagination |
| `idx_watchlist_product_status_notify` | `tb_watchlist(product_id, status, notify_enabled)` | Alert fan-out lookup after price refresh |
| `idx_product_status_id` | `tb_product(status, id)` | Scheduled active-product scan |
| `idx_product_status_updated_at` | `tb_product(status, updated_at)` | Active product list pagination |

Do not add a second username index: `tb_user.username` is already indexed through its unique constraint. See `docs/SQL_INDEX_EXPLAIN.md` for the full reasoning and suggested `EXPLAIN` statements.

## 8. Docker Compose and Environment File Status

`docker-compose.yml` now starts middleware only:

| Service | Image | Exposed ports | Persistent volume |
| --- | --- | --- | --- |
| MySQL | `mysql:8.0` | `3306` | `mysql_data` |
| Redis | `redis:7-alpine` | `6379` | `redis_data` |
| RabbitMQ Management | `rabbitmq:3-management` | `5672`, `15672` | `rabbitmq_data` |

Usage:

```powershell
Copy-Item .env.example .env
docker compose up -d
docker compose ps
```

RabbitMQ Management UI:

```text
http://localhost:15672
```

Static validation status:

- `docker compose config --quiet` passed on 2026-06-01.
- This snapshot does not claim that containers were started or that the real end-to-end broker flow was rerun.

Configuration boundaries to keep in mind:

- Redis and RabbitMQ Spring Boot connection values in `application.yml` support environment-variable overrides.
- MySQL Spring Boot datasource values are still hardcoded to local `root / 123456`; `.env.example` currently configures the MySQL container, not the Spring datasource.
- `.env.example` contains `JWT_SECRET`, but `application.yml` currently uses a hardcoded development JWT secret instead of `${JWT_SECRET:...}`.
- There is no application `Dockerfile`, and the Spring Boot application is intentionally not included in Compose.

## 9. Test Status

The repository contains focused unit and MVC tests for:

- JWT creation, parsing, and interceptor behavior.
- Product CRUD, pagination, detail/price cache hit, database fallback, null cache, cache eviction.
- Redis key generation, TTL jitter, set/get/delete, distributed lock behavior, and rate-limit blocking.
- Watchlist reactivation, duplicate add handling, and watchlist cache reads.
- Price refresh history persistence, target-price alert publishing, producer-side idempotency, batch paging, retries, and single-product failure isolation.
- MQ consumer delegation, consumer-side idempotency skip, and current failure logging behavior.
- Notification persistence, duplicate same-price suppression, above-target suppression, pagination controller, and mark-read controller.
- Global exception handling and application context loading.

Latest verification status:

- `docker compose config --quiet`: passed on 2026-06-01.
- `.\mvnw.cmd -q -DskipTests compile`: passed on 2026-06-01.
- `.\mvnw.cmd -q test`: passed on 2026-06-01.
- RabbitMQ connection-refused logs can appear during tests when a local broker is not running. Unit tests still pass because broker integration is not required for mocked consumer behavior.
- Real MySQL + Redis + RabbitMQ end-to-end verification should be rerun before final project delivery.
- `docs/performance-test.md` is still a template and does not contain real pressure-test results.

## 10. Remaining P0 Tasks

| Priority | Task | Why it remains |
| --- | --- | --- |
| P0 | RabbitMQ consumer failure reliability | Current consumer catches exceptions and returns, so failed notifications can be acknowledged and lost. |
| P0 | RabbitMQ retry and dead-letter path | No DLX/DLQ or bounded retry route exists. Failures are not recoverable through broker topology. |
| P0 | Real middleware end-to-end verification | Compose exists, but the complete flow should be rerun with real containers and recorded evidence. |
| P0 | Real performance-test results | `docs/performance-test.md` contains placeholders rather than measured QPS, P95, P99, error rate, cache, and MQ observations. |

Important P1 follow-ups after the RabbitMQ window:

- Decide whether persistent `mq_message_log` or transactional outbox is needed for a stronger delivery story.
- Fix scheduled refresh transaction boundaries without broad service rewrites.
- Abstract `PriceMockUtil` behind a `PriceProvider` interface.
- Include pagination parameters in watchlist cache keys or clearly restrict the cache strategy.
- Decide whether MySQL datasource and JWT secret should consume environment-variable overrides.

## 11. Recommended Next Window

Enter a dedicated **RabbitMQ reliability hardening** task with minimal scope:

1. Read the current MQ configuration, producer, consumer, tests, and delivery docs.
2. Define the smallest acceptable failure policy: bounded retry plus DLQ, with clear handling for business skips versus retryable failures.
3. Add failing tests for retryable consumer exceptions, DLQ topology, and idempotency-key cleanup or state transition on failure.
4. Implement only the required RabbitMQ configuration and consumer changes.
5. Run compile, tests, Compose static validation, and a real container-based end-to-end verification if Docker is available.
6. Update `README.md` and `docs/RABBITMQ_ASYNC_NOTIFICATION.md` with the verified behavior.

Do not mix this with outbox implementation, transaction-boundary refactoring, frontend work, or real price-provider integration unless explicitly requested.

## 12. Recommended Prompt for the Next Codex Session

```text
你正在接手 Price Tracker 项目的 RabbitMQ 可靠性补强窗口。

先阅读：
1. docs/CODEX_CONTEXT.md
2. docs/RABBITMQ_ASYNC_NOTIFICATION.md
3. README.md
4. src/main/java/com/example/price_tracker/config/RabbitMQConfig.java
5. src/main/java/com/example/price_tracker/mq/producer/PriceAlertProducer.java
6. src/main/java/com/example/price_tracker/mq/consumer/PriceAlertConsumer.java
7. src/test/java/com/example/price_tracker/mq/consumer/PriceAlertConsumerTest.java

然后查看 git status，注意当前工作区可能已有未提交文件，不要覆盖或回退无关改动。

本轮只做 RabbitMQ 失败可靠性闭环的最小补强：
- 区分业务跳过和可重试异常
- 为可重试失败增加有上限的重试策略
- 增加 DLX / DLQ，并明确最终失败消息如何进入 DLQ
- 修正 consumer 失败时 Redis 幂等 key 的处理，避免失败后在 TTL 窗口内无法重试
- 增加对应测试
- 更新 README.md 和 docs/RABBITMQ_ASYNC_NOTIFICATION.md

约束：
- 不要引入微服务
- 不要引入前端
- 不要大规模重构
- 不要顺手改 PriceMockUtil、事务边界或缓存分页问题
- 不要引入 outbox 或 mq_message_log，除非先说明必要性并获得确认
- 不要虚构 Docker 或真实 RabbitMQ 联调结果

完成后运行：
1. git diff --stat
2. docker compose config --quiet
3. .\mvnw.cmd -q -DskipTests compile
4. .\mvnw.cmd -q test

如果 Docker 可用，再启动中间件并完成一次真实链路验证：
注册/登录 -> 创建商品 -> 添加关注 -> 手动刷新价格 -> RabbitMQ Producer -> RabbitMQ Consumer -> tb_notification 落库。

最终输出：
1. 修改文件清单
2. RabbitMQ 重试和 DLQ 设计
3. Redis 幂等失败处理说明
4. 测试与真实联调结果
5. 仍未完成风险
```
