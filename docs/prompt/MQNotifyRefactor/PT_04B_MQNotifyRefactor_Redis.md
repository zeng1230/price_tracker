窗口：MQNotifyRefactor

你现在在 Price Tracker 项目中工作，包名是 com.example.price_tracker。

当前项目第二阶段已经完成 RabbitMQ 异步通知改造。
第三阶段 Foundation 窗口已经提供 Redis Key、Cache Service、分布式锁、幂等基础能力。
WatchNotify 窗口已经在通知触发入口增加了基础幂等保护。

本窗口只负责 MQ 消费侧的高并发与可靠性增强。

任务目标：
增强 RabbitMQ 通知消费者的重复消费保护、失败处理和日志可观测性。

要求：

1. 检查当前 RabbitMQ 通知消息结构。
2. 为通知消息补充或确认唯一业务标识：
    - userId
    - productId
    - targetPrice
    - currentPrice
    - eventTime 或 priceHistoryId
    - messageId，如果已有则复用

3. 消费者幂等：
    - 消费前根据 messageId 或业务字段生成幂等 key
    - 使用 Foundation 的 Redis setIfAbsent
    - 如果幂等 key 已存在，说明消息已处理或正在处理，直接跳过并记录日志
    - 幂等 key 设置合理 TTL

4. 消费失败处理：
    - 单条消息失败不能影响消费者持续运行
    - 保持当前项目已有 ack/nack 策略
    - 如果当前没有明确策略，请补充清晰策略，避免无限重试打爆系统

5. 日志：
    - message received
    - idempotent hit
    - notification send success
    - notification send failed
    - retry or nack decision

6. 测试：
    - 同一消息重复投递只发送一次通知
    - 消费失败时不会导致消费者线程退出
    - 幂等命中时能正常 ack 或跳过

7. 不要修改 Product 和 WatchNotify 的业务代码。
8. 不要重复创建 Redis 工具类。
9. 完成后输出改动文件清单和测试结果。