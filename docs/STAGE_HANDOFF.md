# Price Tracker Stage Handoff

本文档是当前仓库的交接快照，供新的 Codex 会话直接接手。内容基于当前仓库代码、配置、SQL 和文档扫描整理，不新增业务逻辑。

## 1. 当前项目阶段

项目处于第三阶段高并发增强版后的 Integration 交接阶段。

当前主线能力包括：

- 第一阶段基础后端：Spring Boot 单体项目、统一返回、异常处理、MyBatis Plus、MySQL、Redis、JWT、Knife4j。
- 第二阶段业务与 MQ：认证、用户、商品、关注、价格历史、站内通知、RabbitMQ 异步价格提醒。
- 第三阶段高并发增强：Redis 缓存、统一 Redis key、Cache Service、分布式锁、接口限流、商品缓存、关注列表缓存、通知幂等、MQ 消费幂等、价格刷新分页批处理。

## 2. 已完成模块

- Auth/User：注册、登录、当前用户信息查询，JWT 拦截保护非登录接口。
- Product：商品新增、详情、当前价格、分页、更新、逻辑删除。
- Price History：按商品查询价格历史分页。
- Watchlist：添加关注、关注列表、修改目标价、取消关注，支持重复关注幂等和关注缓存。
- Notification：用户通知分页、标记已读、消费价格提醒消息并写入通知。
- RabbitMQ：价格提醒 exchange、queue、routing key、消息体、生产者、消费者。
- Redis：缓存工具、key 管理、分布式锁、限流、商品缓存、关注缓存、通知幂等。
- Scheduler：定时刷新有效商品价格，调用批处理刷新逻辑。
- Docs：README 已补第三阶段说明，`docs/performance-test.md` 已补压测方案。

## 3. 模块关键文件路径

### Foundation

- `pom.xml`
- `src/main/resources/application.yml`
- `src/main/java/com/example/price_tracker/PraceTrackerApplication.java`
- `src/main/java/com/example/price_tracker/common/Result.java`
- `src/main/java/com/example/price_tracker/common/PageResult.java`
- `src/main/java/com/example/price_tracker/common/ResultCode.java`
- `src/main/java/com/example/price_tracker/exception/BusinessException.java`
- `src/main/java/com/example/price_tracker/exception/GlobalExceptionHandler.java`
- `src/main/java/com/example/price_tracker/config/WebMvcConfig.java`
- `src/main/java/com/example/price_tracker/config/MybatisPlusConfig.java`
- `src/main/java/com/example/price_tracker/config/RedisConfig.java`
- `src/main/java/com/example/price_tracker/config/Knife4jConfig.java`

### Auth/User

- `src/main/java/com/example/price_tracker/controller/AuthController.java`
- `src/main/java/com/example/price_tracker/controller/UserController.java`
- `src/main/java/com/example/price_tracker/service/AuthService.java`
- `src/main/java/com/example/price_tracker/service/UserService.java`
- `src/main/java/com/example/price_tracker/service/impl/AuthServiceImpl.java`
- `src/main/java/com/example/price_tracker/service/impl/UserServiceImpl.java`
- `src/main/java/com/example/price_tracker/interceptor/AuthInterceptor.java`
- `src/main/java/com/example/price_tracker/util/JwtTokenUtil.java`
- `src/main/java/com/example/price_tracker/context/UserContext.java`
- `src/main/java/com/example/price_tracker/entity/User.java`
- `src/main/java/com/example/price_tracker/mapper/UserMapper.java`

### Product/Price

- `src/main/java/com/example/price_tracker/controller/ProductController.java`
- `src/main/java/com/example/price_tracker/controller/InternalProductController.java`
- `src/main/java/com/example/price_tracker/service/ProductService.java`
- `src/main/java/com/example/price_tracker/service/PriceService.java`
- `src/main/java/com/example/price_tracker/service/PriceHistoryService.java`
- `src/main/java/com/example/price_tracker/service/impl/ProductServiceImpl.java`
- `src/main/java/com/example/price_tracker/service/impl/PriceServiceImpl.java`
- `src/main/java/com/example/price_tracker/service/impl/PriceHistoryServiceImpl.java`
- `src/main/java/com/example/price_tracker/entity/Product.java`
- `src/main/java/com/example/price_tracker/entity/PriceHistory.java`
- `src/main/java/com/example/price_tracker/mapper/ProductMapper.java`
- `src/main/java/com/example/price_tracker/mapper/PriceHistoryMapper.java`
- `src/main/java/com/example/price_tracker/vo/ProductDetailVo.java`
- `src/main/java/com/example/price_tracker/vo/ProductPageVo.java`
- `src/main/java/com/example/price_tracker/vo/ProductPriceVo.java`
- `src/main/java/com/example/price_tracker/util/PriceMockUtil.java`

### WatchNotify

- `src/main/java/com/example/price_tracker/controller/WatchlistController.java`
- `src/main/java/com/example/price_tracker/controller/NotificationController.java`
- `src/main/java/com/example/price_tracker/service/WatchlistService.java`
- `src/main/java/com/example/price_tracker/service/NotificationService.java`
- `src/main/java/com/example/price_tracker/service/impl/WatchlistServiceImpl.java`
- `src/main/java/com/example/price_tracker/service/impl/NotificationServiceImpl.java`
- `src/main/java/com/example/price_tracker/entity/Watchlist.java`
- `src/main/java/com/example/price_tracker/entity/Notification.java`
- `src/main/java/com/example/price_tracker/mapper/WatchlistMapper.java`
- `src/main/java/com/example/price_tracker/mapper/NotificationMapper.java`
- `src/main/java/com/example/price_tracker/vo/WatchlistVo.java`
- `src/main/java/com/example/price_tracker/vo/NotificationVo.java`

### RabbitMQ

- `src/main/java/com/example/price_tracker/config/RabbitMQConfig.java`
- `src/main/java/com/example/price_tracker/mq/message/PriceAlertMessage.java`
- `src/main/java/com/example/price_tracker/mq/producer/PriceAlertProducer.java`
- `src/main/java/com/example/price_tracker/mq/consumer/PriceAlertConsumer.java`

### Redis

- `src/main/java/com/example/price_tracker/redis/RedisKeyManager.java`
- `src/main/java/com/example/price_tracker/redis/RedisCacheService.java`
- `src/main/java/com/example/price_tracker/redis/RedisDistributedLock.java`
- `src/main/java/com/example/price_tracker/redis/RedisRateLimiter.java`
- `src/main/java/com/example/price_tracker/redis/RedisRateLimitAspect.java`
- `src/main/java/com/example/price_tracker/redis/RedisRateLimitProperties.java`
- `src/main/java/com/example/price_tracker/redis/RateLimit.java`

### Scheduler

- `src/main/java/com/example/price_tracker/task/PriceRefreshTask.java`
- `src/main/java/com/example/price_tracker/PraceTrackerApplication.java`

### Database Schema

- Flyway is the schema owner.
- Runtime migrations live under `src/main/resources/db/migration/`.
- `src/main/resources/sql/` is legacy/reference only and must not be mounted for database initialization.

## 4. 当前业务链路

### Auth/User

1. `POST /api/auth/register` 进入 `AuthController.register`。
2. `AuthServiceImpl.register` 校验用户名唯一，使用 `PasswordEncoder` 加密密码，写入 `tb_user`。
3. `POST /api/auth/login` 进入 `AuthServiceImpl.login`，校验密码和用户状态后生成 JWT。
4. `AuthInterceptor` 拦截 `/api/**` 中除 `/api/auth/**` 等白名单外的请求，解析 Bearer token 并写入 `UserContext`。
5. `GET /api/users/me` 通过 `UserServiceImpl.getCurrentUser` 查询当前用户并返回 `UserVo`。

### Product

1. `POST /api/products` 新增商品，默认 `status = 1`。
2. `GET /api/products/{id}` 查询商品详情，优先读取 Redis 商品详情缓存。
3. `GET /api/products/{id}/price` 查询商品当前价格，优先读取 Redis 商品价格缓存。
4. 缓存未命中时尝试 Redis 分布式锁，回源 MySQL 后写入缓存。
5. 商品不存在或已失效时写入短 TTL 空值缓存，避免穿透。
6. `PUT /api/products/{id}` 和 `DELETE /api/products/{id}` 更新 MySQL 后清理商品详情、价格和空值缓存。

### Watchlist/Notification

1. `POST /api/watchlist` 添加关注；重复有效关注直接返回已有关注 ID。
2. `GET /api/watchlist/my` 查询当前用户关注列表，优先读取 Redis 用户关注缓存。
3. `PUT /api/watchlist/{id}` 修改目标价并清理关注缓存。
4. `DELETE /api/watchlist/{id}` 将关注状态改为 0 并清理关注缓存。
5. `GET /api/notifications/my` 查询当前用户通知分页。
6. `PUT /api/notifications/{id}/read` 将当前用户自己的通知标记已读。

### Price Refresh

1. `POST /api/internal/products/{id}/refresh-price` 手动刷新单商品价格。
2. `PriceServiceImpl.refreshProductPrice` 查询有效商品，使用 `PriceMockUtil.generateNextPrice` 生成新价格。
3. 价格变化时更新商品、清理商品缓存、写入 `tb_price_history`。
4. 查询该商品开启通知的有效关注记录。
5. 达到目标价时通过 Redis `setIfAbsent` 做通知入口幂等，成功后投递 RabbitMQ 消息。

## 5. RabbitMQ 通知链路

- Exchange：`price.alert.exchange`
- Queue：`price.alert.queue`
- Routing key：`price.alert`
- Exchange 类型：`DirectExchange`
- 消息转换器：`Jackson2JsonMessageConverter`

链路：

1. `PriceServiceImpl.sendAlertIfNotDuplicate` 基于 `userId:productId:targetPrice` 生成通知入口幂等 key。
2. 幂等 key 写入成功后构造 `PriceAlertMessage`。
3. `PriceAlertProducer.send` 发送消息到 `price.alert.exchange`，routing key 为 `price.alert`。
4. `PriceAlertConsumer.consume` 监听 `price.alert.queue`。
5. 消费者优先使用 `messageId` 生成 Redis 消费幂等 key；没有 `messageId` 时使用业务字段兜底。
6. 幂等命中时直接跳过。
7. 幂等通过后调用 `NotificationService.consumePriceAlert`。
8. `NotificationServiceImpl.consumePriceAlert` 再次校验 watchlist 状态、通知开关、价格是否达到目标价。
9. 非重复通知写入 `tb_notification`，并更新 `tb_watchlist.last_notified_price`。
10. 消费异常当前只记录错误日志，不向外重新抛出。

消息体 `PriceAlertMessage` 字段：

- `messageId`
- `userId`
- `productId`
- `watchlistId`
- `currentPrice`
- `targetPrice`
- `productName`
- `triggeredAt`

## 6. Redis 当前使用位置

### Redis 基础设施

- `RedisConfig`：配置 `RedisTemplate<String, Object>`，key 使用 `StringRedisSerializer`，value 使用 `GenericJackson2JsonRedisSerializer`。
- `RedisKeyManager`：统一生成缓存、空值、锁、限流、通知幂等 key。
- `RedisCacheService`：封装 get/set/delete/setIfAbsent/randomTtl。
- `RedisDistributedLock`：使用 `setIfAbsent` 加锁，Lua 脚本校验 owner 后解锁。
- `RedisRateLimiter`：使用 `increment + expire` 做固定窗口限流。
- `RedisRateLimitAspect`：拦截 `@RateLimit` 方法，按 `userId + requestURI` 限流。

### Redis key

- 商品详情缓存：`price-tracker:cache:product:detail:{productId}`
- 商品价格缓存：`price-tracker:cache:product:price:{productId}`
- 用户关注列表缓存：`price-tracker:cache:user:watchlist:{userId}`
- 空值缓存：`price-tracker:cache:null:{businessKey}`
- 分布式锁：`price-tracker:lock:{businessKey}`
- 接口限流：`price-tracker:rate-limit:{userId}:{apiPath}`
- 通知幂等：`price-tracker:idempotent:notify:{businessId}`

### 业务使用

- `ProductServiceImpl`：商品详情缓存、商品价格缓存、空值缓存、击穿锁、TTL 随机偏移、商品变更后缓存失效。
- `WatchlistServiceImpl`：当前用户关注列表缓存，添加/更新/删除关注后失效。
- `PriceServiceImpl`：价格刷新后清理商品缓存，发送通知前使用 Redis 做入口幂等。
- `PriceAlertConsumer`：消费 MQ 消息前使用 Redis 做消费幂等。
- `WatchlistController`：关注写接口使用 `@RateLimit`。

## 7. MySQL 表与核心 Entity 对应关系

| MySQL 表 | Entity | Mapper | 说明 |
| --- | --- | --- | --- |
| `tb_user` | `User` | `UserMapper` | 用户、登录凭证、状态。 |
| `tb_product` | `Product` | `ProductMapper` | 商品基础信息、当前价格、状态、检查时间。 |
| `tb_price_history` | `PriceHistory` | `PriceHistoryMapper` | 商品价格变化历史。 |
| `tb_watchlist` | `Watchlist` | `WatchlistMapper` | 用户关注商品、目标价、通知开关、最近提醒价。 |
| `tb_notification` | `Notification` | `NotificationMapper` | 站内通知、已读状态、发送状态。 |

核心字段约定：

- 时间字段使用 `LocalDateTime`。
- 金额字段使用 `BigDecimal`。
- 商品、关注使用 `status` 做逻辑状态。
- `tb_watchlist` 有 `uk_user_product` 唯一约束。

## 8. 定时任务位置与调用链路

- 定时任务类：`src/main/java/com/example/price_tracker/task/PriceRefreshTask.java`
- 开启调度：`src/main/java/com/example/price_tracker/PraceTrackerApplication.java` 上的 `@EnableScheduling`
- cron 配置：`${price.refresh.cron:0 0/30 * * * ?}`
- 调用链路：
  - `PriceRefreshTask.refreshActiveProducts`
  - `PriceService.refreshActiveProducts`
  - `PriceServiceImpl.refreshActiveProducts`
  - 分页查询 `tb_product` 中 `status = 1` 的商品
  - 对每个商品调用内部刷新逻辑
  - 单商品失败最多重试 2 次，失败后记录日志并继续下一个商品

## 9. 当前已知问题

- RabbitMQ 配置当前在 `application.yml` 中固定为 `localhost:5672`、`guest/guest`，未使用环境变量覆盖。
- `notification.consumer-idempotent.ttl-minutes` 在 `PriceAlertConsumer` 中有默认值 30，但 `application.yml` 当前只配置了 `notification.idempotent.ttl-minutes`。
- MQ 消费异常当前被记录后吞掉，不触发 broker 重投；这符合当前测试，但生产可靠性需要重新评估。
- README 仍保留一段英文 `Stage 2 Addendum`，与中文 RabbitMQ/第三阶段说明存在重复。
- `SwapperConfig.java` 为空类，当前没有实际配置内容。
- `docs/performance-test.md` 只有压测方案模板，真实压测结果仍未填写。
- 当前工作区是脏工作区，包含多窗口代码、测试、文档改动；接手前应先确认是否需要整体提交或拆分提交。

## 10. 下一阶段 Integration 需要做什么

建议下一阶段先做收口，不继续扩展业务：

1. 跑 `mvn -q -DskipTests compile` 和 `mvn -q test`，记录结果。
2. 若本地 RabbitMQ 未启动，区分“单测通过”和“真实 MQ 连通未验证”。
3. 检查 README 中重复的 Stage 2 文档，决定合并或保留。
4. 检查 `application.yml`，将 RabbitMQ 配置改为环境变量形式是否符合项目要求。
5. 确认 `notification.consumer-idempotent.ttl-minutes` 是否需要显式写入配置。
6. 检查 `SwapperConfig.java` 是否删除或补充内容。
7. 根据团队要求拆分提交：Redis foundation、Product redis、WatchNotify redis、MQ consumer idempotent、Integration docs。
8. 如要做真实验收，启动 MySQL、Redis、RabbitMQ 后进行接口联调。
9. 如要做压测，按 `docs/performance-test.md` 执行并填写真实结果。

## 11. 本地启动依赖

基础依赖：

- Java 17
- Maven Wrapper：`mvnw.cmd`
- MySQL 8
- Redis
- RabbitMQ

默认连接：

- MySQL：`jdbc:mysql://localhost:3306/price_tracker`
- MySQL 用户名：`root`
- MySQL 密码：`123456`
- Redis：`${REDIS_HOST:localhost}:${REDIS_PORT:6379}`
- RabbitMQ：`localhost:5672`
- RabbitMQ 用户名/密码：`guest/guest`
- 应用端口：`8080`

启动前：

1. 通过 Docker Compose 或手工方式启动 MySQL 8，并创建空数据库 `price_tracker`。
2. 启动 Spring Boot 应用，由 Flyway 自动执行 `src/main/resources/db/migration/` 下的 migration。
3. 启动 Redis。
4. 启动 RabbitMQ。
5. 按需覆盖 Redis 环境变量：`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`REDIS_DATABASE`、`REDIS_TIMEOUT`。

常用命令：

```powershell
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q test
.\mvnw.cmd spring-boot:run
```

Knife4j 文档入口：

```text
http://localhost:8080/doc.html
```

## 12. 推荐的新 Codex 会话启动 Prompt

```text
你正在接手 Price Tracker 项目。请先阅读 docs/STAGE_HANDOFF.md、README.md、docs/performance-test.md，然后检查当前 git status。

当前目标不是新增业务，而是做 Integration 收口：
1. 不要重写项目。
2. 不要无故改 Java 业务代码。
3. 先运行 mvn -q -DskipTests compile。
4. 再运行 mvn -q test。
5. 如果测试失败，先定位根因，再做最小修复。
6. 如果只是 RabbitMQ/Redis/MySQL 未启动导致真实联调不可验证，要明确标注为环境依赖。
7. 检查 README 与 docs/performance-test.md 是否和当前代码一致。
8. 最后输出：修改文件、执行命令、编译/测试结果、仍存在的风险点、是否可以收口。
```
