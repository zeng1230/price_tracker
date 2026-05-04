# Price Tracker 简历与面试交付文档

## 1. 简历项目名称

可选名称：

- Price Tracker 商品价格跟踪后端系统
- 基于 Spring Boot 的商品价格监控与异步通知系统
- 商品价格追踪与 RabbitMQ 异步提醒平台

推荐用于后端实习简历的名称：

```text
Price Tracker 商品价格跟踪后端系统
```

## 2. 简历项目描述

### 精简版

基于 Spring Boot 3 实现商品价格跟踪后端系统，支持用户登录、商品管理、关注目标价、价格刷新和站内通知；使用 Redis 做缓存与幂等控制，RabbitMQ 解耦价格刷新与通知落库，并完成真实端到端联调。

### 完整版

- 基于 Spring Boot 3、MyBatis Plus 和 MySQL 实现用户认证、商品管理、关注列表、价格历史和站内通知等核心模块。
- 使用 JWT 和拦截器维护登录态，业务接口通过 `UserContext` 获取当前用户，实现用户级关注和通知隔离。
- 使用 Redis 缓存商品详情、当前价格和关注列表，并通过空值缓存、分布式锁和 TTL 随机偏移降低缓存穿透、击穿和雪崩风险。
- 使用 RabbitMQ 将价格刷新与通知落库解耦，价格达到目标价后投递提醒消息，由 Consumer 异步写入 `tb_notification`。
- 设计 Redis 入口幂等、MQ 消费幂等和 `last_notified_price` 业务防重，降低重复刷新和重复消费导致的重复通知。
- 补充单元测试和真实端到端联调文档，验证注册/登录、创建商品、添加关注、手动刷新、MQ 消费和通知落库完整链路。

## 3. 技术栈写法

简历可写：

```text
Java 17、Spring Boot 3、MyBatis Plus、MySQL、Redis、RabbitMQ、JWT、Validation、Knife4j、JUnit
```

如果简历空间更紧，可以压缩为：

```text
Spring Boot 3、MyBatis Plus、MySQL、Redis、RabbitMQ、JWT、JUnit
```

## 4. 项目亮点

- 完整业务闭环：实现注册登录、商品创建、关注目标价、价格刷新、价格历史、MQ 异步通知和站内通知查询。
- Redis 缓存与缓存防护：对商品详情、当前价格和关注列表做缓存，并实现空值缓存、分布式锁和 TTL 随机偏移。
- RabbitMQ 异步通知：价格刷新只负责判断和投递消息，Consumer 异步完成通知落库，降低主链路耦合。
- 幂等设计：通过 Redis `setIfAbsent` 控制通知入口和 MQ 消费幂等，并用 `last_notified_price` 做业务层重复通知防护。
- 端到端联调验证：已跑通 `注册/登录 -> 创建商品 -> 添加关注 -> 手动刷新价格 -> MQ 生产消费 -> 写入 tb_notification`。
- 测试覆盖：覆盖 Controller、Service、Redis 工具、MQ Consumer、价格刷新、通知消费和异常处理等关键场景。

## 5. 面试 30 秒介绍

Price Tracker 是我做的一个商品价格跟踪后端项目，核心是用户关注商品并设置目标价，系统刷新价格后，如果达到目标价，就通过 RabbitMQ 异步生成站内通知。项目里我用了 Redis 做商品缓存、关注缓存、限流和幂等控制，也处理了缓存穿透、击穿、雪崩和重复通知问题。最后我用真实 MySQL、Redis、RabbitMQ 跑通了从登录到通知落库的完整链路。

## 6. 面试 2 分钟介绍

Price Tracker 是一个商品价格跟踪后端系统，主要功能包括用户注册登录、商品管理、关注商品、设置目标价、刷新价格、记录价格历史和生成站内通知。业务链路是：用户登录后创建商品，添加关注并设置 `targetPrice`，之后通过手动接口或定时任务刷新商品价格。如果刷新后的价格小于等于目标价，系统会发送价格提醒消息，Consumer 消费后写入通知表。

技术上，Controller 负责接收请求，Service 处理业务逻辑，Mapper 通过 MyBatis Plus 访问 MySQL。用户登录后 JWT 中保存 `userId`，拦截器解析 token 后写入 `UserContext`，后续关注和通知都基于当前用户处理。价格刷新在 `PriceServiceImpl` 中完成：先查询有效商品，用 mock 工具生成新价格，价格变化后更新商品、写入 `tb_price_history`，然后查询该商品的有效关注记录并判断是否达到目标价。

Redis 主要用于提升查询性能和保护后端资源。商品详情和当前价格会缓存，关注列表也会缓存；不存在商品写短 TTL 空值缓存防穿透；热点商品回源前用分布式锁防击穿；正常缓存 TTL 加随机偏移降低雪崩风险。另外，Redis 还用于关注写接口限流、通知入口幂等和 MQ 消费幂等。

RabbitMQ 用来解耦价格刷新和通知落库。刷新链路满足条件后只投递 `PriceAlertMessage` 到 `price.alert.exchange`，Consumer 监听 `price.alert.queue`，再调用通知服务写入 `tb_notification`。为了避免重复通知，我做了三层处理：发送前 Redis 幂等、消费前 Redis 幂等，以及消费时根据 `tb_watchlist.last_notified_price` 判断同一价格是否已经提醒过。

最后我通过真实环境验证了系统：启动 MySQL、Redis、RabbitMQ 后，执行注册登录、创建商品、添加关注、手动刷新价格，日志中能看到 Producer 发布消息、Consumer 收到消息和通知落库成功；数据库中 `tb_price_history` 有价格变化记录，`tb_notification` 有 `TARGET_PRICE_REACHED` 通知记录。

## 7. 面试官追问索引

| 可能追问 | 对应参考 |
| --- | --- |
| 这个项目解决什么问题？ | `docs/INTERVIEW_QA.md` 第 1 题 |
| 为什么使用 RabbitMQ？ | `docs/INTERVIEW_QA.md` 第 2 题 |
| 为什么使用 Redis？ | `docs/INTERVIEW_QA.md` 第 3 题 |
| 如何避免重复通知？ | `docs/INTERVIEW_QA.md` 第 4 题 |
| MQ 消费失败怎么办？ | `docs/INTERVIEW_QA.md` 第 5 题 |
| Redis 和 MySQL 如何保证一致性？ | `docs/INTERVIEW_QA.md` 第 6 题 |
| 为什么 `targetPrice` 放在 `watchlist` 表？ | `docs/INTERVIEW_QA.md` 第 7 题 |
| 定时任务如何刷新价格？ | `docs/INTERVIEW_QA.md` 第 8 题 |
| 用户量变大后的系统瓶颈在哪里？ | `docs/INTERVIEW_QA.md` 第 9 题 |
| 缓存穿透、击穿、雪崩如何处理？ | `docs/INTERVIEW_QA.md` 第 10 题 |
| RabbitMQ 重复消费如何处理？ | `docs/INTERVIEW_QA.md` 第 11 题 |
| 如何证明项目真实跑通？ | `docs/INTERVIEW_QA.md` 第 12 题 |
| 项目中最难的问题是什么？ | `docs/INTERVIEW_QA.md` 第 13 题 |

## 8. 投递实习时如何使用

简历正文建议使用“项目名称 + 技术栈 + 4 到 6 条项目经历”的形式：

```text
Price Tracker 商品价格跟踪后端系统
技术栈：Java 17、Spring Boot 3、MyBatis Plus、MySQL、Redis、RabbitMQ、JWT、JUnit
- 基于 Spring Boot 3 实现用户认证、商品管理、关注目标价、价格刷新、价格历史和站内通知模块。
- 使用 Redis 缓存商品详情、当前价格和关注列表，并通过空值缓存、分布式锁和 TTL 随机偏移降低缓存风险。
- 使用 RabbitMQ 解耦价格刷新和通知落库，价格达到目标价后异步消费消息并写入通知记录。
- 设计 Redis 幂等 key 和 last_notified_price 防重，避免重复刷新或 MQ 重复消费导致重复通知。
- 完成真实端到端联调，验证注册/登录、创建商品、添加关注、手动刷新、MQ 生产消费和通知落库链路。
```

投递前建议准备：

- 先背熟“30 秒介绍”，用于面试开场。
- 再背熟“2 分钟介绍”，用于项目深挖。
- 对照 `docs/INTERVIEW_QA.md` 准备追问，尤其是 RabbitMQ、Redis、幂等、缓存一致性和真实联调证明。
- 简历中不要写“生产级”“高可用完整实现”等当前项目未实现内容，可以写“预留扩展方向”。
