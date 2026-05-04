你负责 Price Tracker 的 PT-04B-MQNotifyRefactor 任务，只处理 RabbitMQ 异步通知改造，不要无故重写整个项目，也不要去改无关模块。

请直接在当前仓库中完成修改并写入文件，不要只输出代码片段。

项目基本信息：
1. 包名统一使用 `com.example.price_tracker`
2. 项目是单体 Spring Boot 3.x
3. Java 17
4. Maven
5. MySQL 8
6. MyBatis Plus
7. Redis
8. JWT
9. Lombok
10. Spring Validation
11. Knife4j

全局开发约束：
1. 所有时间字段使用 `LocalDateTime`
2. 所有金额字段使用 `BigDecimal`
3. 所有接口统一返回 `Result<T>`
4. DTO 负责请求参数
5. VO 负责响应参数
6. 不要直接向前端暴露 entity
7. controller 只做接参与返回
8. service 负责业务逻辑
9. mapper 使用 MyBatis Plus
10. 删除优先采用逻辑删除
11. 如果发现已有代码与要求冲突，优先在当前仓库中做最小必要修改
12. 优先复用已有类，不要重复造轮子
13. 不要把所有文件完整打印在聊天里，除非我明确要求
14. 完成后运行 `mvn -q -DskipTests compile` 验证
15. 如果编译失败，优先继续修复可以修复的问题
16. 最后只汇报：
- 修改了哪些文件
- 执行了哪些命令
- 当前编译是否通过
- 还剩什么阻塞点

当前项目背景：
第一版已经完成以下能力：
1. 用户注册登录
2. JWT 鉴权
3. 商品新增、详情、分页、修改、逻辑删除
4. 关注商品、设置目标价、取消关注
5. 查询我的关注列表
6. 查询价格历史
7. 查询我的通知
8. 标记通知已读
9. 内部刷新单个商品价格
10. 定时批量刷新商品价格
11. mock 价格波动
12. 当前通知链路大概率还是同步实现

你这次的目标：
把“达到目标价后直接写通知表”的同步逻辑，改造成 RabbitMQ 异步通知链路。

改造目标范围：
1. 接入 RabbitMQ 基础设施
2. 新增消息体 `PriceAlertMessage`
3. 新增生产者 `PriceAlertProducer`
4. 新增消费者 `PriceAlertConsumer`
5. 改造 `PriceService.refreshProductPrice(Long productId)`
6. 让价格刷新逻辑不再直接写通知表，而是发送 MQ 消息
7. 由消费者异步生成通知记录
8. 消费者侧基于 `last_notified_price` 做简单防重复提醒
9. 保留现有业务接口路径，不要破坏现有对外 API
10. 尽量保持第一版数据模型不大改

RabbitMQ 设计要求：
1. 使用一个 exchange 即可
2. exchange 名称：`price.alert.exchange`
3. queue 名称：`price.alert.queue`
4. routing key：`price.alert`
5. 为了简单清晰，优先使用 DirectExchange
6. 配置消息转换器，优先使用 Jackson JSON

消息体设计要求：
请新增一个专门的消息对象，例如 `PriceAlertMessage`，不要直接传 entity。
消息体至少包含这些字段：
- userId
- productId
- watchlistId
- currentPrice
- targetPrice
- productName
- triggeredAt

核心业务改造要求：
一、PriceService 改造
请检查当前 `refreshProductPrice(Long productId)` 逻辑，并改造成下面流程：
1. 查询商品
2. 获取旧价格
3. 使用现有 mock 逻辑生成新价格
4. 若价格变化，则更新商品 `current_price`
5. 插入 `tb_price_history`
6. 查询关注该商品的有效 watchlist
7. 判断：
    - notify_enabled = 1
    - target_price 不为空
    - current_price <= target_price
8. 满足条件时，不再直接写 `tb_notification`
9. 改为发送 `PriceAlertMessage` 到 RabbitMQ

二、消费者逻辑
消费者收到消息后，需要：
1. 校验消息内容
2. 查询对应 watchlist 或直接利用消息字段
3. 做简单幂等判断
4. 幂等规则优先基于 `tb_watchlist.last_notified_price`
5. 如果本次价格和 `last_notified_price` 相同，则不要重复生成通知
6. 如果需要通知：
    - 写入 `tb_notification`
    - 更新 `tb_watchlist.last_notified_price`
7. 打印必要日志
8. 出现异常时记录错误日志，不要静默吞掉

三、幂等与一致性要求
1. 生产者侧只做业务过滤，不做最终幂等
2. 消费者侧负责最终防重
3. 当前版本接受“最终一致性”
4. 不需要上来实现本地消息表、事务消息、死信队列
5. 但代码结构要尽量为后续扩展预留空间

四、配置改造要求
1. 在 `pom.xml` 中补充 RabbitMQ 依赖
2. 在 `application.yml` 中补充 RabbitMQ 配置项
3. 新增 RabbitMQ 配置类，例如：
    - exchange
    - queue
    - binding
    - message converter
4. 如项目已有配置类，请在现有基础上最小修改

五、代码结构建议
请尽量在现有项目结构中新增或调整以下内容：
- `config/RabbitMQConfig.java`
- `mq/message/PriceAlertMessage.java`
- `mq/producer/PriceAlertProducer.java`
- `mq/consumer/PriceAlertConsumer.java`

如果当前项目没有 `mq` 包，你可以直接创建：
`com.example.price_tracker.mq`

六、注意事项
1. 不要重复实现商品 CRUD
2. 不要重复实现认证模块
3. 不要推翻现有 watchlist、notification、price_history 表结构
4. 不要随意修改已有对外接口路径
5. 如果现有代码中通知逻辑分散，请做适度整理，但保持最小必要修改
6. 如果需要修改 `NotificationService`、`WatchlistService`、`PriceService`、`InternalController`、`Scheduler` 等，请直接改
7. 如果需要补充内部方法，请优先放到 service 层
8. 如果 RabbitMQ 未启动，不要求你真的连通外部服务，但代码必须达到可编译状态
9. 如果你能做到，尽量让消费者方法和业务逻辑保持清晰分层，不要把所有逻辑堆到监听方法里

验收目标：
1. 项目成功引入 RabbitMQ 相关依赖和配置
2. 价格刷新链路已改为“发消息”而不是“同步写通知”
3. 消费者能够异步生成通知记录
4. 消费者基于 `last_notified_price` 做了简单防重
5. 项目至少达到 `mvn -q -DskipTests compile` 可通过

你的执行步骤建议：
1. 先检查当前仓库结构和已有实现
2. 找到当前通知生成的同步逻辑位置
3. 接入 RabbitMQ 基础设施
4. 新建消息体、生产者、消费者
5. 改造 PriceService
6. 必要时调整 NotificationService / WatchlistService
7. 编译验证
8. 汇报结果

现在开始直接修改当前仓库，不要只给方案。
完成后只汇报：
- 修改了哪些文件
- 执行了哪些命令
- 当前编译是否通过
- 还剩哪些阻塞点