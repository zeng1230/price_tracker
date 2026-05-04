窗口：Foundation

你现在在 Price Tracker 项目中工作，包名是 com.example.price_tracker。

当前项目已经完成：
1. 第一阶段基础单体业务
2. 第二阶段 RabbitMQ 异步通知改造

现在进入第三阶段：高并发增强版。

本窗口只负责第三阶段的通用基础设施，不要改具体业务逻辑。

任务目标：
为后续 Product、WatchNotify、MQNotifyRefactor 等业务窗口提供统一的 Redis、缓存 key、分布式锁、限流基础能力。

要求：

1. 检查项目是否已有 Redis 依赖与配置。
    - 如果没有，补充 Spring Boot 3.x Redis 依赖
    - 补充 application.yml Redis 配置项
    - 不要写死 host、port、password，使用配置项

2. 新增统一 Redis Key 管理类。
    - 商品详情缓存 key
    - 商品价格缓存 key
    - 用户关注列表缓存 key
    - 缓存空值 key 或空值标记
    - 分布式锁 key
    - 接口限流 key
    - 通知幂等 key

3. 新增 Redis 缓存工具或 Cache Service。
    - 支持 get
    - 支持 set with TTL
    - 支持 delete
    - 支持 setIfAbsent，用于锁和幂等
    - 支持 TTL 随机偏移方法

4. 新增 Redis 分布式锁工具。
    - tryLock
    - unlock
    - 锁必须有 TTL
    - unlock 时要避免误删别人的锁
    - 如果实现复杂，可以使用 Lua 脚本或 value 校验

5. 新增简单限流基础能力。
    - 优先使用注解 + AOP 或拦截器
    - 支持按用户 ID + 接口维度限流
    - 限流次数和窗口时间从配置读取
    - 本阶段只完成基础设施，不要求立刻覆盖所有业务接口

6. 增加统一异常或错误返回。
    - 被限流时返回明确错误
    - 不要返回模糊的系统异常

7. 添加基础测试。
    - Redis get/set/delete
    - 分布式锁 tryLock/unlock
    - 限流超过阈值时被拦截

8. 不要改动 Product、WatchNotify、MQNotifyRefactor 的业务代码。
9. 不要重构无关代码。
10. 完成后输出本次改动文件清单，以及后续业务窗口应该如何接入这些基础能力。

请先分析现有项目结构，再做最小侵入式改造。