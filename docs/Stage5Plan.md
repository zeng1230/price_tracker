# Price Tracker Stage 5 Integration & Delivery Plan

> **For Codex / agentic workers:** This plan is an execution handbook for Stage 5 delivery. It must be implemented task by task with verifiable outputs. Do not add new business logic, do not introduce microservices or frontend, and do not perform large-scale refactoring.

**Goal:** Complete the Integration & Delivery stage for Price Tracker by verifying the full backend chain, consolidating delivery documentation, improving observability handoff, and producing a repeatable demo path.

**Project:** Price Tracker  
**Package:** `com.example.price_tracker`  
**Tech Stack:** Spring Boot 3, Java 17, MySQL, MyBatis Plus, Redis, RabbitMQ, JWT, Maven Wrapper  
**Stage:** Stage 5 - Integration & Delivery  

---

## 1. Current Project Status

Price Tracker is currently a runnable Spring Boot backend with a complete base business loop. The project has finished the Stage 3 high-concurrency enhancement work and is ready for integration closure.

Completed capabilities:

- User authentication and current-user query based on JWT.
- Product creation, detail query, price query, pagination, update, and logical delete.
- Watchlist creation, target-price update, cancel watch, duplicate-watch idempotency, and watchlist cache invalidation.
- Price refresh by internal API and scheduled task.
- Price history persistence after price changes.
- RabbitMQ asynchronous price-alert notification chain.
- Consumer-side notification persistence into `tb_notification`.
- Redis cache, null-value cache, TTL jitter, distributed lock, rate limiting, and notification idempotency.
- Unit tests for core service, controller, Redis, MQ consumer, exception, and common response behavior.
- Pressure-test plan document exists and Stage 3 technical closure is mostly documented.

Stage 5 should not expand the product scope. It should turn the current backend into a deliverable project that another engineer, interviewer, or reviewer can run, verify, and understand.

---

## 2. Stage 5 Goals

### 2.1 Full-Chain Regression Verification

Verify the complete chain from login to asynchronous notification persistence:

1. Register and login a user.
2. Query current user.
3. Create a product.
4. Query product detail and current price.
5. Add product to watchlist with target price.
6. Trigger product price refresh.
7. Verify price history is written.
8. Verify RabbitMQ producer publishes a price alert when target price is reached.
9. Verify RabbitMQ consumer receives the message.
10. Verify notification is persisted and can be queried.
11. Mark notification as read.

### 2.2 Documentation Consolidation

Consolidate project delivery documentation so the project can be understood without reading source code first:

- README local setup and run commands.
- Database table purpose and SQL initialization order.
- Redis key inventory and invalidation rules.
- RabbitMQ exchange, queue, routing key, message payload, and consumer flow.
- Core API verification commands.
- Known limitations and non-goals.

### 2.3 Engineering Observability

Make the delivery checklist explicitly verify observability, without adding new product logic:

- Important logs must include enough business identifiers to trace a request or async event.
- Trace identifier strategy must be documented or added with minimal infrastructure-only changes if missing.
- Unified exception behavior must be verified through existing `GlobalExceptionHandler`.
- Actuator Health should be checked or added as a delivery-level health endpoint if not already present.

### 2.4 Demo Closure

Produce two repeatable backend demo paths:

- Normal user path: auth, product query, watchlist, notification query, mark read.
- Admin/internal path: product management and internal price refresh API.

No frontend is required. Demo must use runnable commands such as PowerShell `Invoke-RestMethod`, curl, Knife4j, or documented HTTP examples.

---

## 3. Core Execution Steps

### Module A: Environment Baseline

**Input:**

- Local Java 17 environment.
- Maven Wrapper: `mvnw.cmd`.
- MySQL 8, Redis, and RabbitMQ running locally.
- SQL files under `src/main/resources/sql/`.
- Configuration in `src/main/resources/application.yml`.

**Steps:**

1. Confirm Java and Maven Wrapper availability.
2. Confirm MySQL, Redis, and RabbitMQ are running.
3. Initialize database in the documented SQL order.
4. Compile the project.
5. Run the full test suite.
6. Start the application locally on port `8080`.

**Output:**

- A clean local runtime baseline.
- Captured compile and test results.
- Application running at `http://localhost:8080`.
- Knife4j available at `http://localhost:8080/doc.html`.

**Verification:**

```powershell
java -version
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q test
.\mvnw.cmd spring-boot:run
```

Expected result:

- Compile succeeds.
- Tests pass or documented environment-only failures are clearly identified.
- Application starts without startup exception.

---

### Module B: Database Delivery Verification

**Input:**

- SQL files:
  - `src/main/resources/sql/00_init_database.sql`
  - `src/main/resources/sql/tb_user.sql`
  - `src/main/resources/sql/tb_product.sql`
  - `src/main/resources/sql/tb_price_history.sql`
  - `src/main/resources/sql/tb_watchlist.sql`
  - `src/main/resources/sql/tb_notification.sql`

**Steps:**

1. Verify every SQL file can be executed from a clean database.
2. Document table responsibility and key business fields.
3. Verify table-to-entity mapping:
   - `tb_user` -> `User`
   - `tb_product` -> `Product`
   - `tb_price_history` -> `PriceHistory`
   - `tb_watchlist` -> `Watchlist`
   - `tb_notification` -> `Notification`
4. Confirm `tb_watchlist.uk_user_product` supports duplicate watch idempotency.

**Output:**

- Database initialization section in README or delivery document.
- Table responsibility table.
- Initialization command block.

**Verification:**

```powershell
mysql -uroot -p123456 < src/main/resources/sql/00_init_database.sql
mysql -uroot -p123456 price_tracker < src/main/resources/sql/tb_user.sql
mysql -uroot -p123456 price_tracker < src/main/resources/sql/tb_product.sql
mysql -uroot -p123456 price_tracker < src/main/resources/sql/tb_price_history.sql
mysql -uroot -p123456 price_tracker < src/main/resources/sql/tb_watchlist.sql
mysql -uroot -p123456 price_tracker < src/main/resources/sql/tb_notification.sql
```

Expected result:

- All tables are created successfully.
- Application can insert and query all required tables through the full-chain regression.

---

### Module C: Redis Delivery Verification

**Input:**

- `RedisConfig`
- `RedisKeyManager`
- `RedisCacheService`
- `RedisDistributedLock`
- `RedisRateLimiter`
- `RedisRateLimitAspect`
- Redis-related tests.

**Steps:**

1. Document Redis connection environment variables:
   - `REDIS_HOST`
   - `REDIS_PORT`
   - `REDIS_PASSWORD`
   - `REDIS_DATABASE`
   - `REDIS_TIMEOUT`
2. Document Redis key inventory:
   - `price-tracker:cache:product:detail:{productId}`
   - `price-tracker:cache:product:price:{productId}`
   - `price-tracker:cache:user:watchlist:{userId}`
   - `price-tracker:cache:null:{businessKey}`
   - `price-tracker:lock:{businessKey}`
   - `price-tracker:rate-limit:{userId}:{apiPath}`
   - `price-tracker:idempotent:notify:{businessId}`
3. Document source-of-truth and invalidation rules:
   - MySQL remains authoritative.
   - Product update/delete/price refresh invalidates product detail and price cache.
   - Watchlist add/update/delete invalidates current user's watchlist cache.
   - Notification idempotency keys expire by TTL.
4. Run Redis-focused unit tests.

**Output:**

- Redis key and invalidation documentation.
- Redis verification evidence.

**Verification:**

```powershell
.\mvnw.cmd -q -Dtest=RedisCacheServiceTest,RedisDistributedLockTest,RedisKeyManagerTest,RedisRateLimitAspectTest test
```

Expected result:

- Redis key generation, cache service, lock, and rate-limit behavior tests pass.
- README or delivery document explains each key's business meaning.

---

### Module D: RabbitMQ Delivery Verification

**Input:**

- `RabbitMQConfig`
- `PriceAlertMessage`
- `PriceAlertProducer`
- `PriceAlertConsumer`
- `NotificationServiceImpl.consumePriceAlert`

**Steps:**

1. Document RabbitMQ topology:
   - Exchange: `price.alert.exchange`
   - Queue: `price.alert.queue`
   - Routing key: `price.alert`
   - Exchange type: `DirectExchange`
   - Message converter: `Jackson2JsonMessageConverter`
2. Document `PriceAlertMessage` fields:
   - `messageId`
   - `userId`
   - `productId`
   - `watchlistId`
   - `currentPrice`
   - `targetPrice`
   - `productName`
   - `triggeredAt`
3. Verify producer log appears after target price is reached.
4. Verify consumer log appears after message delivery.
5. Verify notification record is inserted into `tb_notification`.
6. Document current limitation: consumer failure handling currently relies on logging and idempotency TTL; no DLQ/retry expansion should be added in Stage 5 unless explicitly approved.

**Output:**

- RabbitMQ chain documentation.
- Async notification verification commands.
- Known limitation statement.

**Verification:**

Run the full-chain regression in Module F and confirm logs include:

```text
Publishing price alert message
Published price alert message successfully
Received price alert message from queue=price.alert.queue
Created price alert notification
Notification send success
```

Expected result:

- Message is produced.
- Consumer receives it.
- Notification can be queried through `GET /api/notifications/my`.

---

### Module E: Observability and Health

**Input:**

- `GlobalExceptionHandler`
- Controller validation annotations.
- Service logs in product, price refresh, Redis, RabbitMQ, and notification paths.
- `application.yml`
- `pom.xml`

**Steps:**

1. Verify unified exception response shape through invalid request examples.
2. Confirm important logs contain business identifiers:
   - `userId`
   - `productId`
   - `watchlistId`
   - `messageId`
   - Redis key or RabbitMQ queue when applicable.
3. Decide and document trace strategy:
   - If traceId already exists, document how it appears in logs.
   - If traceId is missing, add only minimal infrastructure support in Stage 5 after approval; do not change business logic.
4. Verify Actuator Health:
   - If `spring-boot-starter-actuator` exists, document `/actuator/health`.
   - If missing, record as a Stage 5 infrastructure task and add only the dependency/config needed for health check after approval.
5. Document expected health states for MySQL, Redis, RabbitMQ, and application startup.

**Output:**

- Observability checklist.
- Health-check section.
- Exception response examples.

**Verification:**

```powershell
Invoke-RestMethod -Method Get "http://localhost:8080/actuator/health"
```

Expected result:

- If Actuator is enabled, endpoint returns application health.
- If Actuator is not enabled yet, Stage 5 delivery document must explicitly state the gap and the minimal approved change needed.

---

### Module F: Full-Chain Regression Script

**Input:**

- Running application at `http://localhost:8080`.
- MySQL, Redis, and RabbitMQ running.
- Existing REST APIs.

**Steps:**

1. Register a unique user.
2. Login and capture JWT.
3. Query current user.
4. Create a product.
5. Query product detail.
6. Query product current price.
7. Add product to watchlist with target price.
8. Trigger internal price refresh.
9. Query price history.
10. Query notifications.
11. Mark the first notification as read if a notification is produced.

**Output:**

- A copy-paste runnable PowerShell regression script.
- Captured IDs:
  - `userId`
  - `productId`
  - `watchlistId`
  - `notificationId`
- Captured evidence:
  - product response
  - price history response
  - notification response
  - relevant logs

**Verification Script:**

```powershell
$base = "http://localhost:8080"
$username = "stage5_user_" + (Get-Date -Format "yyyyMMddHHmmss")
$password = "123456"

Invoke-RestMethod -Method Post "$base/api/auth/register" `
  -ContentType "application/json" `
  -Body (@{
    username = $username
    password = $password
    email = "$username@example.com"
    nickname = "Stage5 User"
  } | ConvertTo-Json)

$login = Invoke-RestMethod -Method Post "$base/api/auth/login" `
  -ContentType "application/json" `
  -Body (@{
    username = $username
    password = $password
  } | ConvertTo-Json)

$headers = @{ Authorization = "Bearer " + $login.data.token }

$me = Invoke-RestMethod -Method Get "$base/api/users/me" -Headers $headers

$product = Invoke-RestMethod -Method Post "$base/api/products" `
  -Headers $headers `
  -ContentType "application/json" `
  -Body (@{
    productName = "Stage5 Demo Product"
    productUrl = "https://example.com/stage5-product"
    platform = "demo"
    currentPrice = 100.00
    currency = "USD"
    imageUrl = "https://example.com/stage5-product.png"
  } | ConvertTo-Json)

$productId = $product.data

Invoke-RestMethod -Method Get "$base/api/products/$productId" -Headers $headers
Invoke-RestMethod -Method Get "$base/api/products/$productId/price" -Headers $headers

$watch = Invoke-RestMethod -Method Post "$base/api/watchlist" `
  -Headers $headers `
  -ContentType "application/json" `
  -Body (@{
    productId = $productId
    targetPrice = 200.00
    notifyEnabled = 1
  } | ConvertTo-Json)

$watchlistId = $watch.data

Invoke-RestMethod -Method Post "$base/api/internal/products/$productId/refresh-price" -Headers $headers

Start-Sleep -Seconds 2

$history = Invoke-RestMethod -Method Get "$base/api/products/$productId/price-history?pageNum=1&pageSize=10" -Headers $headers
$notifications = Invoke-RestMethod -Method Get "$base/api/notifications/my?pageNum=1&pageSize=10" -Headers $headers

if ($notifications.data.records.Count -gt 0) {
  $notificationId = $notifications.data.records[0].id
  Invoke-RestMethod -Method Put "$base/api/notifications/$notificationId/read" -Headers $headers
}

[PSCustomObject]@{
  username = $username
  userId = $me.data.id
  productId = $productId
  watchlistId = $watchlistId
  priceHistoryCount = $history.data.records.Count
  notificationCount = $notifications.data.records.Count
}
```

Expected result:

- User registration and login succeed.
- Product and watchlist are created.
- Price refresh completes.
- Price history contains at least one record when price changes.
- Notification appears when generated price reaches target.
- Notification read API succeeds when a notification exists.

---

### Module G: Demo Closure

**Input:**

- Full-chain regression script.
- Knife4j page.
- README and delivery docs.

**Steps:**

1. Prepare normal user demo path:
   - Register/login.
   - View current user.
   - Query product detail and price.
   - Add watchlist.
   - View watchlist.
   - View notification.
   - Mark notification read.
2. Prepare admin/internal demo path:
   - Create product.
   - Update product.
   - Trigger internal price refresh.
   - Query price history.
3. Document demo assumptions:
   - Internal API is still protected by JWT unless current code allows otherwise.
   - Price is generated by mock utility.
   - Notification depends on RabbitMQ being available.
4. Record fallback explanation:
   - If RabbitMQ is unavailable, synchronous business APIs can still be tested, but async notification persistence is not fully verified.

**Output:**

- Demo section in README or delivery document.
- One normal-user command block.
- One admin/internal command block.
- Demo checklist with pass/fail boxes.

**Verification:**

- A new engineer can run the demo commands from a clean local setup.
- Demo output includes product ID, watchlist ID, price history result, and notification result.

---

### Module H: Documentation Packaging

**Input:**

- Existing docs:
  - `README.md`
  - `docs/PROJECT_REVIEW.md`
  - `docs/INTERVIEW_QA.md`
  - `docs/performance-test.md`
  - `docs/STAGE_HANDOFF.md`
  - `docs/RESUME_DELIVERY.md`

**Steps:**

1. Update README as the primary entry point:
   - Project overview.
   - Tech stack.
   - Local dependencies.
   - SQL initialization.
   - Configuration variables.
   - Compile/test/run commands.
   - Core API list.
   - Full-chain demo.
   - Redis/RabbitMQ summary.
   - Known limitations.
2. Keep detailed review/interview material in docs instead of overloading README.
3. Ensure `docs/performance-test.md` states:
   - Test objective.
   - Dataset.
   - Concurrency model.
   - Metrics to capture.
   - Result table.
   - Bottleneck analysis.
4. Add a Stage 5 delivery checklist document if README would become too long.
5. Remove duplicated or stale delivery descriptions only after confirming the same content exists in the new docs.

**Output:**

- README can serve as the project delivery homepage.
- Stage 5 plan and checklist are easy to follow.
- Existing docs are linked instead of duplicated.

**Verification:**

- New reader can start from README and run the project without asking for hidden setup steps.
- Every external dependency has a default value or environment-variable override documented.
- All command blocks are copy-paste runnable for PowerShell or clearly marked as shell-specific.

---

## 4. Delivery Standards

Stage 5 is accepted only when all standards below are met.

### 4.1 Local Environment Is Runnable

- MySQL, Redis, and RabbitMQ setup is documented.
- SQL initialization order is documented and verified.
- Application compiles with Maven Wrapper.
- Test suite can be run with one command.
- Application can start locally.

Acceptance command:

```powershell
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q test
.\mvnw.cmd spring-boot:run
```

### 4.2 Core Chain Is Verifiable

- Register/login works.
- JWT-protected APIs work.
- Product creation and query work.
- Watchlist creation works.
- Price refresh works.
- Price history can be queried.
- RabbitMQ consumer can persist notification.
- Notification can be queried and marked read.

Acceptance evidence:

- PowerShell regression script output.
- Application logs for producer and consumer.
- Notification query response.

### 4.3 Documentation Is Complete and Readable

- README is the first entry point.
- Database, Redis, RabbitMQ, and API verification are documented.
- Known limitations are explicit.
- Stage 5 plan remains aligned with current code and does not describe unimplemented features as completed.

### 4.4 Logs Are Traceable

- Logs include business identifiers on important paths.
- MQ logs include `messageId`, `productId`, `watchlistId`, and `userId` where available.
- Redis logs include key names where useful.
- Error responses are unified and documented.
- Health endpoint status is documented or the missing Actuator gap is explicitly recorded.

---

## 5. Codex Execution Constraints

Codex must follow these constraints during Stage 5:

1. Do not add new business logic.
2. Do not introduce microservices.
3. Do not introduce a frontend.
4. Do not perform large-scale refactoring.
5. Do not change public API contracts unless explicitly required for delivery verification.
6. Do not change DTO/VO response shapes unless a bug prevents existing documented APIs from working.
7. Do not replace MySQL, Redis, RabbitMQ, JWT, or MyBatis Plus.
8. Keep changes limited to documentation, verification scripts, configuration health checks, and minimal observability infrastructure.
9. Every produced command must be directly executable or clearly marked as environment-specific.
10. Every delivery claim must be backed by a command, response, log, or test result.

---

## 6. Execution Order and Responsibility Windows

| Order | Task Window | Owner | Scope | Input | Output | Verification |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Environment Baseline | Backend/Codex | Confirm local dependencies, compile, tests, startup | Java 17, Maven Wrapper, MySQL, Redis, RabbitMQ | Clean local runtime baseline | `compile`, `test`, `spring-boot:run` |
| 2 | Database Delivery | Backend/Codex | Verify SQL initialization and document table responsibilities | SQL files | DB setup docs and table mapping | MySQL table creation succeeds |
| 3 | Redis Delivery | Backend/Codex | Document Redis keys, TTL, invalidation, and run Redis tests | Redis classes and tests | Redis key inventory | Redis-focused tests pass |
| 4 | RabbitMQ Delivery | Backend/Codex | Document MQ topology and verify async notification chain | MQ config, producer, consumer | MQ chain docs and log evidence | Producer/consumer logs and notification query |
| 5 | Observability & Health | Backend/Codex | Verify logs, exceptions, trace strategy, and health endpoint | Logs, exception handler, config | Observability checklist | Invalid API response and health check evidence |
| 6 | Full-Chain Regression | Backend/Codex | Execute auth-product-watch-refresh-notification path | Running app and dependencies | Regression script output with IDs | Script succeeds and notification is persisted |
| 7 | Demo Closure | Backend/Codex | Prepare normal-user and admin/internal demo paths | Regression commands and Knife4j | Demo checklist and command blocks | New runner can reproduce demo |
| 8 | Documentation Packaging | Backend/Codex | Consolidate README and docs links | Existing docs | Delivery-ready README and Stage 5 docs | README can bootstrap the project |
| 9 | Final Acceptance | Team/Reviewer | Review evidence and sign off | All prior outputs | Stage 5 accepted or gaps listed | Checklist is fully pass/fail recorded |

Recommended responsibility windows:

- **Window 1 - Baseline and database:** Tasks 1-2. Finish before editing delivery docs.
- **Window 2 - Middleware verification:** Tasks 3-4. Finish before full-chain regression.
- **Window 3 - Observability:** Task 5. Finish before demo recording.
- **Window 4 - Regression and demo:** Tasks 6-7. Finish with captured output.
- **Window 5 - Packaging and sign-off:** Tasks 8-9. Finish after all evidence is available.

---

## 7. Final Stage 5 Acceptance Checklist

- [ ] Local dependencies are documented.
- [ ] SQL initialization succeeds from a clean database.
- [ ] `.\mvnw.cmd -q -DskipTests compile` succeeds.
- [ ] `.\mvnw.cmd -q test` succeeds or environment-only gaps are recorded.
- [ ] Application starts locally.
- [ ] Knife4j documentation page is reachable.
- [ ] Redis keys and invalidation rules are documented.
- [ ] RabbitMQ topology and message payload are documented.
- [ ] Full-chain regression script is runnable.
- [ ] Price history can be verified.
- [ ] Notification consumer persistence can be verified.
- [ ] Logs contain enough identifiers to trace the async chain.
- [ ] Health endpoint status is documented.
- [ ] README is delivery-ready.
- [ ] Known limitations and non-goals are explicit.
