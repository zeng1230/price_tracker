继续当前 Price Tracker 项目，不要修改核心业务逻辑。

当前项目已经完成 Integration 阶段：
注册/登录 -> 创建商品 -> 添加关注 -> 手动刷新价格 -> RabbitMQ Producer -> RabbitMQ Consumer -> 写入 tb_notification

现在进入项目交付与面试准备阶段。

请新增或更新以下文档：

1. docs/PROJECT_REVIEW.md
   内容包括：
- 项目定位
- 核心业务链路
- 技术链路
- 数据库核心表设计
- Redis 使用点
- RabbitMQ 异步通知链路
- 幂等设计
- 端到端联调验证结果
- 遇到的问题与解决方案
- 后续可扩展方向

2. docs/INTERVIEW_QA.md
   准备至少 12 个面试高频问题和参考答案，必须包括：
- 为什么使用 RabbitMQ
- 为什么使用 Redis
- 如何避免重复通知
- MQ 消费失败怎么办
- Redis 和 MySQL 如何保证一致性
- 为什么 targetPrice 放在 watchlist 表
- 定时任务如何刷新价格
- 用户量变大后的系统瓶颈
- 缓存穿透、击穿、雪崩如何处理
- RabbitMQ 重复消费如何处理
- 如何证明项目真实跑通
- 项目中最难的问题是什么

3. README.md
   新增“简历项目描述”和“面试讲解入口”小节，要求简洁，不要重复大段内容。

约束：
- 不要新增业务功能
- 不要修改 Service、Controller、Mapper 逻辑
- 不要重构项目
- 所有内容必须基于当前真实实现
- 输出修改文件清单和验证方式

完成后运行现有测试，确认没有误改业务代码。