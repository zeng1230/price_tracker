task1：
不要修改代码，先做分析。

请扫描整个项目，输出以下内容：

1. 用户登录链路（Controller → Service → JWT → userId 获取）
2. 商品模块（ProductController / Service / Mapper / 表）
3. 关注模块（Watch）
4. 定时任务（Task 类位置 + 调用链）
5. 价格刷新逻辑（PriceService）
6. 价格判断逻辑位置
7. RabbitMQ Producer 位置
8. RabbitMQ Consumer 位置
9. exchange / queue / routingKey 配置
10. Redis 使用位置

然后输出：

【集成断点清单】
逐条标出：
- 哪些已经打通
- 哪些缺失
- 哪些实现了但没有接入

task2：
不要修改代码。

从集成断点清单中，优先修复最关键的链路断点：

目标：
跑通一条最小链路：

关注商品 → 手动触发价格刷新 → 触发 MQ → 消费 → 写入提醒记录

要求：
1. 每次只修一个断点
2. 不要大规模重构
3. 每次修改说明：
    - 改了哪些文件
    - 为什么改
    - 如何验证