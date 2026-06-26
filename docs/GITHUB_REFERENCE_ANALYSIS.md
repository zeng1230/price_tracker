# GitHub 参考项目对比分析

> 分析日期：2026-06-20  
> 分析范围：当前 `Price Tracker` 仓库，以及下列 5 个 GitHub 仓库的 README、构建文件和核心代码结构。  
> 结论边界：只提炼可迁移的设计思路，不复制参考代码，不建议改变 `com.example.price_tracker` 包名、单体架构或既有技术栈。

## 一、当前 Price Tracker 项目现状

### 1. 已有核心功能

当前项目已经形成完整的后端业务闭环，而不只是基础 CRUD：

1. 用户注册、登录、JWT 签发和接口鉴权。
2. 商品创建、详情、当前价格、分页、更新和软删除。
3. 用户关注商品、设置目标价、修改提醒开关、取消关注。
4. 手动或定时刷新价格，记录价格历史。
5. 当 `currentPrice <= targetPrice` 时触发价格提醒。
6. RabbitMQ 异步消费后生成站内通知，支持通知分页和标记已读。
7. 定时任务分页扫描有效商品；单个商品失败会重试并与其他商品隔离。
8. Redis 缓存、空值缓存、分布式锁、接口限流和消息幂等。
9. Knife4j 接口文档、Actuator 健康检查、TraceId 日志及 MQ 链路透传。

核心链路为：

```text
注册/登录 -> 创建商品 -> 添加关注并设置目标价
-> 手动或定时刷新价格 -> 更新商品价格并写价格历史
-> 命中阈值 -> RabbitMQ -> 消费者幂等校验
-> 写入站内通知 -> 用户查询并标记已读
```

### 2. 已有技术栈

| 类别 | 当前实现 |
| --- | --- |
| 语言与框架 | Java 17、Spring Boot 3.3.4、Spring MVC、Validation、AOP |
| 持久化 | MySQL 8、MyBatis-Plus 3.5.7、Flyway schema migration |
| 缓存与协调 | Spring Data Redis、RedisTemplate |
| 异步消息 | Spring AMQP、RabbitMQ |
| 安全 | JWT（JJWT 0.12.6）、BCrypt |
| 文档与运维 | Knife4j/OpenAPI、Actuator、MDC TraceId |
| 测试 | JUnit 5、Mockito、Spring Boot Test |
| 本地环境 | Docker Compose 启动 MySQL、Redis、RabbitMQ；应用本地运行 |

### 3. 已有 Redis 使用点

| 场景 | 当前实现 | 评价 |
| --- | --- | --- |
| 商品详情缓存 | `price-tracker:cache:product:detail:{id}` | 合理，适合读多写少 |
| 商品当前价格缓存 | `price-tracker:cache:product:price:{id}` | 与价格刷新后的缓存失效配套 |
| 用户关注列表缓存 | `price-tracker:cache:user:watchlist:{userId}` | 已在关注变更后主动删除 |
| 缓存穿透 | 不存在的数据写短 TTL 空值 key | 已具备基础防护 |
| 缓存击穿 | 回源前使用 Redis 分布式锁 | 已有工程化意识 |
| 缓存雪崩 | TTL 随机抖动 | 适合当前规模 |
| 接口限流 | `@RateLimit` + Redis 计数器 | 已用于关注相关写接口 |
| Producer 防重复 | 用户、商品、目标价组成 Redis 幂等 key | 可压制同一阈值短时间重复发送 |
| Consumer 幂等 | 优先使用 `messageId` 写 Redis 幂等 key | 失败时删除 key，让消息可重试 |

当前 Redis 使用已经超过“接入中间件”的展示层级，能够说明缓存一致性、热点保护、限流和幂等。但验收文档需要明确 key、TTL、触发条件、验证命令和失败语义，否则面试或评审时不容易复现。

### 4. 已有 RabbitMQ 使用点

当前消息拓扑如下：

```text
PriceServiceImpl
-> PriceAlertProducer
-> price.alert.exchange (DirectExchange)
-> price.alert.queue
-> PriceAlertConsumer
-> NotificationServiceImpl
-> tb_notification / tb_watchlist.last_notified_price
```

已具备：

- 持久化 Direct Exchange、Queue 和 routing key。
- Jackson JSON 消息转换。
- `PriceAlertMessage.messageId`。
- TraceId 写入消息 header 并在消费者恢复到 MDC。
- Listener 重试，默认 3 次，指数间隔。
- 主队列绑定死信交换机和死信队列。
- Producer 发送异常时回滚 Redis 防重复 key。
- Consumer Redis 幂等；消费异常时删除幂等 key 并抛出异常。
- 落库前再次检查关注状态、提醒开关、阈值和最近提醒价格。

仍需明确的可靠性边界：

- Producer 当前没有显式 publisher confirm/return 闭环；`convertAndSend` 正常返回不等价于 Broker 已可靠接收。
- 数据库事务提交与消息发送不是原子操作，仍可能出现“数据库回滚但消息已发出”或“数据库已提交但消息未发出”。
- Redis TTL 幂等不是永久约束，通知表也没有基于业务事件的唯一键。
- 已配置 DLQ，但还需要文档化失败注入、重试次数、最终入 DLQ 和人工处置步骤。

这些问题不要求当前阶段立刻引入事务外盒，但必须在文档中准确声明边界。

### 5. 已有 MySQL 表或 Entity

| 表 / Entity | 主要职责 | 关键字段或约束 |
| --- | --- | --- |
| `tb_user` / `User` | 用户身份 | `username` 唯一、密码摘要、邮箱、状态 |
| `tb_product` / `Product` | 商品目录与最新价格 | URL、平台、当前价、币种、上次检查时间、状态 |
| `tb_price_history` / `PriceHistory` | 价格变更历史 | 旧价、新价、采集时间、来源 |
| `tb_watchlist` / `Watchlist` | 用户与商品关注关系 | 目标价、提醒开关、最近提醒价格；`(user_id, product_id)` 唯一 |
| `tb_notification` / `Notification` | 站内通知 | 通知类型、内容、已读状态、发送状态、发送时间 |

项目还提供了与主要查询匹配的联合索引，包括通知列表、价格历史、关注列表、阈值扫描、有效商品扫描和商品分页。当前 SQL 没有声明外键，适合简化实习项目部署，但需要依靠业务层维持引用完整性。

### 6. 当前项目相对真实项目还薄弱的地方

1. **数据源耦合**：`PriceServiceImpl` 直接依赖 `PriceMockUtil`，没有 `PriceProvider` 接口、标准报价对象、平台路由和失败分类。
2. **角色与管理员边界缺失**：`User` 没有角色字段，商品创建、修改、删除和内部刷新接口没有清晰的管理员授权模型。
3. **数据库版本治理不足**：SQL 文件依靠首次 Docker 初始化或手工执行，缺少可追踪、可增量、可回滚评估的 migration 流程。
4. **消息可靠性仍是演示级**：已有重试、DLQ 和双层幂等，但缺少 publisher confirm、数据库唯一事件键或事务外盒。
5. **真实通知渠道缺失**：当前只有站内通知。对实习项目而言不是阻塞项，但要把“站内通知”明确为产品选择，而不是暗示已经发送邮件或短信。
6. **趋势能力较薄**：已有价格历史，但还没有最低价、最高价、均价、近 N 天涨跌幅等低成本聚合接口。
7. **接口语义可加强**：`/api/internal/products/{id}/refresh-price` 的 `internal` 命名没有对应内部鉴权或管理员权限。
8. **验收链路分散**：已有多份 Redis、RabbitMQ 和交付文档，但 README、Swagger、演示脚本与故障验证之间仍可建立更清晰的导航。
9. **配置安全性**：Redis/RabbitMQ 已支持环境变量，MySQL 连接和 JWT 默认值仍应统一改为环境变量驱动并强调仅用于本地开发。
10. **集成验证不足**：单元测试较丰富，但真实 MySQL、Redis、RabbitMQ 组合下的端到端验收仍主要依赖人工脚本。

## 二、5 个参考项目分别值得借鉴什么

### 1. `zxc3buttons/price-monitoring-system-on-spring-boot`

项目地址：[price-monitoring-system-on-spring-boot](https://github.com/zxc3buttons/price-monitoring-system-on-spring-boot)

#### 1. 项目定位

Java 单体价格监控 REST 应用，重点是用户认证、商品目录、市场渠道、价格动态和价格比较。

#### 2. 技术栈

Java 11、Spring Boot、Spring MVC、Spring Data JPA、PostgreSQL、Spring Security、JWT、Swagger/OpenAPI、Docker 和 Docker Compose。

#### 3. 业务模型

核心模型包括 `User`、`Role`、`Category`、`Product`、`Marketplace` 和 `Item`。其中 Product 表示标准商品，Marketplace 表示渠道，Item 表示某商品在某渠道下的价格记录或可售项。该拆分比当前项目单一 `Product.platform` 更适合多平台比价。

#### 4. 工程结构

采用 controller、service、repository、entity、dto、security、exception 分层；提供数据库初始化脚本、ERD、Dockerfile、Docker Compose 和服务测试。

#### 5. 可借鉴点

- README 对技术栈、功能清单、安装命令和 API 文档入口的组织方式。
- `Product`、`Marketplace`、`Item` 分层表达“标准商品”和“平台报价”的思路。
- DTO 与 Entity 分离、统一异常处理、JWT 过滤器和角色模型。
- ERD、数据库初始化、容器化运行和服务层测试共同构成可验收交付。
- 价格按日查询、价格差和跨平台比较等产品化查询形态。

#### 6. 不建议照搬的点

- 不迁移 PostgreSQL、JPA 或其包结构；当前 MySQL + MyBatis-Plus 已稳定。
- 不为追求模型完整立即拆分 Product、Marketplace、Item 三套表，会扩大本轮改造范围。
- 不复制其旧版本安全配置、DTO 映射或异常代码。
- 不把“支持多平台比价”写成当前已实现能力。

#### 7. 能迁移到 Price Tracker 的具体建议

1. P0：在 README 增加一张核心表关系图和一条完整演示链路。
2. P0：补充 Docker 启动、SQL 初始化、Swagger、健康检查和测试命令的统一入口。
3. P1：管理员接口使用清晰角色模型，不再让普通登录用户操作商品目录和内部刷新。
4. P1：先通过 `PriceProvider` 封装平台差异，暂不拆表；只有出现“一商品多平台报价”的真实需求后再评估 Marketplace/Offer 模型。

### 2. `jain-18/Price_drop_alert`

项目地址：[Price_drop_alert](https://github.com/jain-18/Price_drop_alert)

#### 1. 项目定位

面向终端用户的价格下降提醒 Web 应用，包含注册登录、Dashboard、商品 URL 监控、目标价、邮件提醒和管理员页面。

#### 2. 技术栈

Java 17、Spring Boot 3.2.3、Spring MVC、Spring Data JPA、MySQL、Spring Security、Thymeleaf、JavaMail、Jsoup。

#### 3. 业务模型

`User` 持有角色和被监控商品集合，`Product` 持有名称、URL、目标价和所属用户；管理员维护一套用于价格查询的商品数据。定时任务每 10 秒扫描商品，命中目标价后发送邮件。

#### 4. 工程结构

包含用户 Controller、管理员 Controller、JPA Repository、Entity、价格检查服务、邮件服务、模板和静态资源。它更接近“完整产品演示”，但前后端和抓价逻辑耦合较紧。

#### 5. 可借鉴点

- 用户关注目标价是核心业务对象，而不是把目标价塞进商品公共属性。
- Dashboard 所表达的用户视角：我的监控、当前价、目标价、状态和操作。
- 管理员维护商品数据与普通用户管理关注项的权限区分。
- 命中阈值后通知用户的直观产品闭环。

#### 6. 不建议照搬的点

- 不引入 Thymeleaf、静态页面或完整 Dashboard；当前项目定位是后端能力展示。
- 不使用 Jsoup 做高频、站点强耦合的爬虫，也不采用固定 10 秒全表扫描。
- 不把邮件发送放在价格扫描主流程中；当前 RabbitMQ 解耦更合理。
- 不使用 `double` 表示金额，应继续使用 `BigDecimal`。
- README 声称邮件或短信，但代码能力需要分别核验；Price Tracker 文档不能写未实现功能。

#### 7. 能迁移到 Price Tracker 的具体建议

1. P0：以 API 和 Swagger 替代 Dashboard，提供“我的关注”和“我的通知”的演示请求与响应。
2. P1：增加最小管理员接口雏形，例如用户状态查询、商品状态管理和手动刷新，但必须先定义角色授权。
3. P1：增加关注状态、目标价命中状态等可读字段，不新增前端。
4. P2：未来将邮件作为 `NotificationChannel` 的独立消费者实现，而不是耦合到价格刷新服务。

### 3. `harounchebbi/CRUD-springboot-mysql-redis-rabbitmq`

项目地址：[CRUD-springboot-mysql-redis-rabbitmq](https://github.com/harounchebbi/CRUD-springboot-mysql-redis-rabbitmq)

#### 1. 项目定位

用户管理 CRUD 示例，README 强调 MySQL、Redis、RabbitMQ、JWT、Swagger 和事件驱动更新，主要价值是中间件组合与本地启动说明。

#### 2. 技术栈

Java 8、Spring Boot 2.1.6、Spring Data JPA、MySQL、Spring Data Redis/Jedis、Spring AMQP、Spring Security、JWT、Swagger 2、Actuator，并包含 WebSocket。

#### 3. 业务模型

业务模型主要是 `User` 和认证请求，明显比 Price Tracker 薄。创建和删除用户会发布应用内事件，再通过 WebSocket 广播更新。

#### 4. 工程结构

包含 controller、service、repository、entity、security、config、events 和异常处理。Redis 通过 `RedisTemplate` 在服务层手工读写用户缓存。

#### 5. 可借鉴点

- README 将 MySQL、Redis、消息、Swagger 和本地运行拆开说明，适合用作中间件验收文档模板。
- 缓存配置与业务服务分离、JWT 配置对象化、事件对象独立定义。
- 将“业务变化”表达为事件，而不是让 Controller 直接操作消息设施。

#### 6. 不建议照搬的点

- 技术版本明显过旧，不迁移 Java 8、Spring Boot 2.1、Springfox、旧 JWT 和 `WebSecurityConfigurerAdapter`。
- 不引入 WebSocket；当前项目不需要实时推送。
- 不复制其 Redis 序列化和缓存代码；当前项目的 key 管理、TTL、空值和锁更完整。
- **重要核验结论**：虽然 README 和 `pom.xml` 提到 RabbitMQ/Spring AMQP，但本次检查的核心代码主要使用 Spring 应用事件和 WebSocket，未看到完整 RabbitMQ Producer/Consumer 业务链路。因此只能借鉴组合说明，不能把它当作 RabbitMQ 可靠性实现范本。

#### 7. 能迁移到 Price Tracker 的具体建议

1. P0：为 Redis 和 RabbitMQ 分别写“场景、配置、触发、观测、预期结果、故障注入”六段式验收说明。
2. P0：在 README 明确 MySQL、Redis、RabbitMQ 的启动依赖及验证命令。
3. P1：继续保持消息 DTO 与业务 Service 解耦，不引入其 WebSocket 链路。
4. P1：把当前 Redis/MQ 测试和人工验收入口集中到一个文档索引。

### 4. `aparaschiveiadrian/currency-alert`

项目地址：[currency-alert](https://github.com/aparaschiveiadrian/currency-alert)

#### 1. 项目定位

实时加密货币价格监控和阈值提醒平台，目标是展示微服务拆分、低延迟价格事件、可靠通知和失败恢复。

#### 2. 技术栈

Java 21、Spring Boot 3.5、PostgreSQL、Redis Pub/Sub、RabbitMQ、Spring Security/JWT、Flyway、Resilience4j、Docker Compose，以及可选 Prometheus、Loki、Grafana。

#### 3. 业务模型

主要包含用户、加密资产、价格、提醒规则和待发送通知。提醒规则支持目标价格和方向（高于或低于），通知失败会进入 `pending_notifications` 后续重试。

#### 4. 工程结构

拆为 API Gateway、Auth、Rate、Alert、Notification、数据库迁移和共享 JWT 库等模块。价格更新通过 Redis Pub/Sub 传播，命中提醒后通过 RabbitMQ 投递给通知服务；数据库 migration 独立管理。

#### 5. 可借鉴点

- 将“价格已更新”和“提醒已触发”区分为不同事件语义。
- RabbitMQ 用于需要可靠交付的通知，Redis Pub/Sub 只用于可丢失、低延迟的价格广播。
- Flyway migration 按版本管理用户、资产、提醒和待发送通知表。
- 通知失败落库、定时重试、指数退避和清晰的失败状态。
- Postman 集合、架构图、Docker Compose 和健康检查形成可演示工程交付。

#### 6. 不建议照搬的点

- 不拆微服务，不增加 API Gateway，不迁移 PostgreSQL。
- 不引入 Redis Pub/Sub；当前定时任务在单体内直接调用服务更简单可靠。
- 不引入 Keycloak、独立认证服务、SSE 或复杂前端。
- 不引入整套 Prometheus、Loki、Grafana；当前 Actuator + TraceId 足够。
- 不因“可靠性”一次性引入熔断、复杂重试平台和事务外盒，先把现有边界验证清楚。

#### 7. 能迁移到 Price Tracker 的具体建议

1. P0：补全 RabbitMQ 重试、DLQ、幂等和 TraceId 的验收链路。
2. P1：引入 Flyway 管理当前 MySQL schema，仍保持单体和现有表结构。
3. P1：若增加邮件渠道，新增简单的通知发送状态和失败原因，并由现有 RabbitMQ Consumer 侧扩展。
4. P2：只有出现明确的一致性事故或更高可靠性目标后，再评估事务外盒和待发送通知重试表。

### 5. `Alaa-abdulridha/amazon-price-tracker-serpapi`

项目地址：[amazon-price-tracker-serpapi](https://github.com/Alaa-abdulridha/amazon-price-tracker-serpapi)

#### 1. 项目定位

以 SerpApi 获取 Amazon 商品数据的 Python 价格追踪应用，强调商品搜索、价格历史、趋势分析、智能提醒和多渠道通知。

#### 2. 技术栈

Python 3.9+、FastAPI、SQLAlchemy、SQLite/可配置数据库、SerpApi、机器学习分析、邮件、Slack、桌面通知和 Web Dashboard。

#### 3. 业务模型

包含 Product、PriceHistory、PriceAlert、PricePrediction、NotificationLog、APIUsage 和 SystemMetrics。商品可配置检查间隔和多个通知渠道；价格历史支持趋势与预测。

#### 4. 工程结构

`core` 负责追踪编排，`services` 负责 SerpApi 和价格监控，`database` 管理模型，`ai` 管理预测，`notifications` 管理多渠道发送，`web` 暴露 Dashboard/API，并配有较多测试。

#### 5. 可借鉴点

- 外部数据获取应集中在独立客户端或适配器，不散落在业务服务中。
- 报价结果除了价格，还可包含币种、商品标题、平台商品标识、抓取时间和原始来源。
- 价格历史可先提供简单统计和趋势，不必直接上机器学习。
- 通知渠道、通知日志与价格提醒规则应是不同职责。
- 外部 API 需要超时、重试、限流、缓存和可测试的模拟响应。

#### 6. 不建议照搬的点

- 不改为 Python/FastAPI，不引入 SQLite/SQLAlchemy。
- 不强制依赖 SerpApi，不在核心业务里直接 `new SerpApiClient`。
- 不引入机器学习预测、AI 推荐、Plotly Dashboard 或桌面通知。
- 不承诺实时价格；外部数据源受配额、延迟和平台条款约束。
- 该仓库自身更接近具体 SerpApi 客户端，而不是完整的 Java `PriceProvider` SPI；Price Tracker 应吸收“隔离外部数据源”的思想并重新设计接口。

#### 7. 能迁移到 Price Tracker 的具体建议

1. P1：定义 `PriceProvider` 接口和不可变 `PriceQuote`，由 `MockPriceProvider` 提供默认实现。
2. P1：通过 `platform` 或 provider code 路由数据源，未知平台明确失败，不静默回退到真实调用。
3. P1：将 `source`、`capturedAt`、`currency` 写入报价和价格历史，保留现有 mock 可重复测试。
4. P2：以后新增真实 Provider 时再实现超时、重试、限流、熔断和外部 API 配额观测。
5. P2：先做近 7/30 天最低价、最高价、均价、涨跌幅，再考虑预测模型。

## 三、参考优先级

### A 类：当前阶段强参考

| 项目 | 强参考范围 | 原因 |
| --- | --- | --- |
| `price-monitoring-system-on-spring-boot` | README、ERD、API 组织、Docker、认证、商品/平台模型思想 | 与 Java 单体价格项目最接近 |
| `Price_drop_alert` | 用户关注、目标价、管理员边界、产品演示流程 | 能补足业务模型和用户视角 |
| `CRUD-springboot-mysql-redis-rabbitmq` | 本地启动、Redis/RabbitMQ/Swagger 分项说明 | 与当前中间件组合一致，但只参考文档组织和接入思路 |

### B 类：当前阶段弱参考

| 项目 | 只吸收的思想 | 当前不直接落地的部分 |
| --- | --- | --- |
| `currency-alert` | 事件语义、RabbitMQ 可靠性、Flyway、失败通知重试 | 微服务、Gateway、Redis Pub/Sub、复杂韧性和监控栈 |
| `amazon-price-tracker-serpapi` | 数据源适配、标准报价、价格趋势、通知渠道职责 | Python/FastAPI、SerpApi 强依赖、AI 预测、复杂 Dashboard |

### C 类：未来进阶参考

- 真实数据源：可插拔 Provider、API 配额、超时、重试、限流和来源追踪。
- 事务外盒：解决数据库提交和 MQ 发布之间的一致性窗口；不能把 Redis 幂等误称为事务外盒。
- 微服务：只有团队规模、独立伸缩和独立发布需求成立时再评估。
- 可观测性：业务指标、队列积压、DLQ 告警、Provider 成功率和延迟。
- 实时推送：只有前端产品明确需要时再评估 SSE/WebSocket。

## 四、最终借鉴清单

### P0：强烈建议马上补

1. 整理 README：项目定位、技术栈、架构图、表关系、完整业务链路、API 索引和限制声明。
2. 提供单一“本地启动与验收”入口：环境变量、Docker Compose、SQL、启动、健康检查、Swagger、测试。
3. 提供可复制的端到端演示：注册、登录、创建商品、关注、刷新、价格历史、通知、已读。
4. 提供 Redis 验收：key、TTL、命中/失效、空值、锁、限流和幂等验证命令。
5. 提供 RabbitMQ 验收：拓扑、正常消费、重试、DLQ、重复消息、TraceId 和失败边界。
6. 修正文档中的能力声明，只描述实际实现；明确当前是 Mock 数据源和站内通知。
7. 统一 docs 导航，避免多份阶段文档互相重复而没有主入口。

### P1：可以作为当前阶段补强

1. 定义最小角色模型和管理员接口雏形，保护商品目录管理和手动刷新。
2. 引入 `PriceProvider` + `PriceQuote` + `MockPriceProvider`，替换 Service 对 `PriceMockUtil` 的直接依赖。
3. 增加简单价格趋势聚合：近 N 天最低、最高、平均、涨跌幅，不做 AI 预测。
4. 评估并引入 Flyway，将现有 SQL 整理为可重复、可增量的 MySQL migration。
5. 增加 RabbitMQ publisher confirm/return 验证和 DLQ 人工处置说明。
6. 为通知增加稳定业务事件标识；必要时用数据库唯一约束增强长期幂等。
7. 增加至少一条基于真实 MySQL、Redis、RabbitMQ 的集成验收链路，可先用脚本或 Testcontainers 单独评估。

### P2：以后进阶再考虑

1. 真实 Amazon 或其他平台 Provider，以及 SerpApi 可选适配器。
2. 邮件、Webhook 等多渠道通知。
3. 事务外盒、待发送事件表和自动补偿任务。
4. 业务指标、队列积压告警和完整监控面板。
5. 多平台 Offer/Marketplace 独立建模和跨平台比价。
6. SSE/WebSocket 实时推送。
7. 机器学习预测、智能推荐或复杂趋势评分。
8. 微服务拆分。

## 五、不要做的事情

当前阶段明确不建议：

- 不改成微服务，不增加 API Gateway 或服务注册发现。
- 不新增 PostgreSQL、MongoDB、Elasticsearch 等数据库。
- 不改包名 `com.example.price_tracker`，不推翻当前 controller/service/mapper/entity 分层。
- 不把 Java 项目改成 Python/FastAPI。
- 不新增 Thymeleaf、React、Vue 或复杂 Dashboard。
- 不引入 Keycloak 或独立认证服务。
- 不引入 SSE、WebSocket 或 Redis Pub/Sub 来追求“实时化”。
- 不把 SerpApi 设为必选依赖，不把 API key 写入仓库。
- 不建设高频爬虫、代理池、验证码绕过或站点反爬对抗系统。
- 不直接复制参考仓库的源码、数据库结构或安全配置。
- 不引入 Prometheus + Grafana + Loki 全家桶来替代当前必要的业务开发。
- 不建设复杂后台管理系统；管理员接口只做最小可验收能力。
- 不直接引入 AI/机器学习价格预测。
- 不为了“架构高级”同时引入事务外盒、熔断、延迟队列和复杂补偿平台。
- 不把 Redis 幂等、RabbitMQ 重试或 DLQ 描述成 exactly-once；应明确它们提供的是有限窗口内的重复抑制和失败隔离。

## 六、推荐落地方案

### 总体原则

保持单体架构、MySQL、Redis、RabbitMQ、JWT 和现有包结构。下一轮以“文档能复现、链路能验收、边界能解释”为目标，只做小步增量。

### 1. README 和 docs 补强

建议先完成，不依赖业务代码变化：

1. README 顶部增加项目定位、明确不包含的能力和 3 分钟理解版架构图。
2. 增加 MySQL 表关系图和 Redis/RabbitMQ 拓扑图。
3. 增加 docs 总索引，将 SQL、Redis、RabbitMQ、演示交付和项目评审文档串联起来。
4. 将环境变量、本地启动、编译测试、健康检查、Swagger 和端到端演示合并为一条路径。
5. 增加“可靠性边界”章节，说明没有事务外盒、真实通知渠道和真实 Provider。

### 2. 业务模型补强

最小方案：

- 保留现有五张核心表。
- 为用户增加简单角色字段时，只支持 `USER`、`ADMIN` 两类，不设计权限表、菜单表或 RBAC 平台。
- 价格趋势优先基于 `tb_price_history` 做查询聚合，不新增预测表。
- 暂不拆 Product/Marketplace/Offer；先让 `PriceProvider` 隔离平台差异。

### 3. 管理员接口雏形

建议只提供：

- 分页查询用户及状态。
- 启用/禁用用户。
- 管理商品状态。
- 手动触发单商品价格刷新。

管理员能力必须通过 JWT 角色校验；不新增管理前端。现有 `/api/internal/products/{id}/refresh-price` 可在下一轮重命名或加管理员保护，避免“internal”只有命名没有安全语义。

### 4. Redis/RabbitMQ 验证说明

Redis 文档至少覆盖：

- 第一次查询回源、第二次查询命中。
- 商品更新和价格刷新后的缓存删除。
- 不存在商品的空值缓存。
- 热点并发回源时的分布式锁。
- 关注写接口限流。
- Producer 和 Consumer 幂等 key 的 TTL 与失败删除行为。

RabbitMQ 文档至少覆盖：

- Exchange、Queue、routing key、DLX、DLQ。
- 正常消息从发送到通知落库。
- Consumer 连续失败后的重试和 DLQ。
- 重复投递相同 `messageId` 时只处理一次。
- TraceId 从 HTTP/MDC 到消息 header 再到 Consumer 日志。
- 当前没有 publisher confirm 和事务外盒的限制。

### 5. 本地启动和演示链路

推荐固定验收顺序：

```text
1. 复制 .env.example 并启动 docker compose
2. 验证 MySQL、Redis、RabbitMQ
3. 启动应用并检查 /actuator/health
4. 打开 Knife4j
5. 注册并登录获取 JWT
6. 创建商品
7. 添加关注并设置容易命中的目标价
8. 手动刷新价格
9. 查询价格历史
10. 查询通知并标记已读
11. 在 Redis 和 RabbitMQ Management UI 核验中间状态
```

脚本应输出关键 ID、HTTP 状态、TraceId 和最终断言，避免只给散落的 curl 命令。

### 6. Swagger 或接口文档补充

在不更换 Knife4j 的前提下：

- 为认证、商品、关注、价格历史、通知和内部/管理员接口分组。
- 补充 JWT Bearer 安全方案。
- 补充请求示例、成功响应、常见错误码、分页参数和金额字段语义。
- 明确哪些接口需要普通用户，哪些需要管理员。
- Swagger 是接口契约入口，README 只保留核心 API 表和链接。

### 7. PriceProvider 数据源抽象设计

建议下一轮采用最小接口，不改变数据库和主流程：

```java
public interface PriceProvider {
    String providerCode();
    boolean supports(Product product);
    PriceQuote fetchPrice(Product product);
}
```

`PriceQuote` 至少包含：

```text
price、currency、source、capturedAt、externalProductId（可选）、productTitle（可选）
```

落地顺序：

1. 将现有随机价格逻辑包装成 `MockPriceProvider`。
2. `PriceServiceImpl` 只依赖 Provider 路由器，不依赖 `PriceMockUtil`。
3. 默认配置只启用 Mock，测试完全不需要网络。
4. 未来新增 `SerpApiPriceProvider` 或其他 Provider 时放在适配层，并由独立配置显式启用。
5. 外部 Provider 失败必须分类记录超时、限流、数据无效和未找到商品，不能写入伪造价格。

这一抽象值得做，但应作为 P1 的独立小改造，不与管理员、Flyway 或真实 API 接入捆绑。

### 8. Flyway 或 SQL migration 是否值得引入

**判断：值得引入，优先级 P1，不是本轮 P0。**

理由：

- 当前已有 5 张表和多组索引，继续依靠 Docker 首次初始化会让后续字段与索引变化难以追踪。
- Flyway 支持 MySQL，不需要更换数据库，也不要求微服务。
- 对实习项目而言，版本化 migration 能直接展示 schema 演进和环境一致性。

建议采用一次受控迁移：

1. 先确定现有数据库是允许重建的开发库，还是必须保留数据的已有库。
2. 新环境将现有建表和索引整理为 `V1__init_schema.sql`。
3. 已有环境评估 `baseline-on-migrate` 或手工 baseline，不能直接在未知数据状态下启用。
4. 后续每次变更使用新的 `V2__...sql`，不修改已经执行的 migration。
5. Docker 初始化和 Flyway 只能有一个 schema owner，避免两套机制同时建表。

如果下一轮只剩很短时间，应先把现有 SQL 初始化与验证文档写清楚，再单独安排 Flyway；不应为了依赖名称好看而仓促接入。

## 七、建议的下一轮开发范围

建议下一轮只选择一个可独立验收的小批次：

1. **首选：文档与验收批次**——README/docs 索引、Swagger 说明、Redis/RabbitMQ 故障验收和完整演示脚本。
2. **次选：PriceProvider 批次**——接口、标准报价、Mock 实现、路由和单元测试，不接真实 API。
3. **再下一批：管理员与角色批次**——最小 `USER/ADMIN` 权限和少量管理接口。
4. **独立批次：Flyway 迁移**——先确认现有数据库基线策略，再替换 Docker 初始化职责。

不要把这四批合并成一次大改。当前项目已经有足够多的中间件能力，下一阶段质量提升主要来自边界清晰、文档一致、故障可验证和数据源可替换。
