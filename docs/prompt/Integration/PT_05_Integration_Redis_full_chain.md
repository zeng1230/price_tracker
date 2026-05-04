窗口：Integration

当前 Price Tracker 第三阶段高并发增强版已经完成以下子任务：

1. Foundation：
    - Redis 配置
    - 统一 Redis Key 管理
    - Cache Service
    - 分布式锁基础能力
    - 限流基础能力

2. Product：
    - 商品详情缓存
    - 商品价格缓存
    - 缓存穿透保护
    - 缓存击穿保护
    - 缓存 TTL 随机偏移，降低雪崩风险
    - 商品价格更新后的缓存失效

3. WatchNotify：
    - 用户关注列表缓存
    - 添加关注、取消关注、修改目标价后的关注缓存失效
    - 重复关注幂等
    - 通知入口幂等
    - 关注相关接口限流

4. MQNotifyRefactor：
    - RabbitMQ 通知消费者幂等
    - 重复消息消费保护
    - 消费失败处理
    - 通知链路日志增强

5. Product 价格刷新批处理：
    - 已确认 PriceRefreshTask.java 位于 task 包
    - @EnableScheduling 位于 PraceTrackerApplication.java
    - PriceRefreshTask 调用 PriceService.refreshActiveProducts()
    - 批处理优化在 PriceServiceImpl.refreshActiveProducts() 中完成
    - 不修改 PriceRefreshTask.java
    - 不修改 @EnableScheduling

现在本窗口只负责第三阶段文档收口、联调说明和压测方案。
不要修改业务代码。
不要修改 Java 源码。
不要新增 Redis、MQ、Product、WatchNotify 业务逻辑。

任务目标：
更新 README，并新增 docs/performance-test.md，形成第三阶段完整交付说明。

要求：

1. 更新 README，新增 Third Stage / 高并发增强版说明，至少包括：
    - Redis 缓存
    - 商品详情缓存
    - 商品价格缓存
    - 用户关注列表缓存
    - 缓存穿透保护
    - 缓存击穿保护
    - 缓存雪崩保护
    - 价格刷新批处理
    - 接口限流
    - 关注幂等
    - 通知入口幂等
    - MQ 消费者幂等
    - 第三阶段测试说明
    - 压测文档位置

2. 在 docs 目录新增或更新 performance-test.md。

3. performance-test.md 包括：
    - 测试目标
    - 测试环境
    - 测试前置条件
    - 测试数据准备
    - 测试接口范围
    - JMeter 测试场景
    - 并发配置
    - 指标说明
    - 测试结果记录表
    - 优化前后对比表
    - 性能瓶颈分析
    - 风险与后续优化方向

4. 压测接口至少包括：
    - 商品详情查询
    - 商品价格查询，如果项目有独立接口
    - 用户关注列表查询
    - 添加关注
    - 修改目标价
    - 手动刷新商品价格，如果项目存在该接口

5. 并发配置写入文档：
    - 50 并发
    - 100 并发
    - 200 并发
    - ramp-up 10 秒
    - 每个场景持续 60 秒

6. 指标至少包括：
    - 平均响应时间
    - P95 响应时间
    - P99 响应时间
    - QPS
    - 错误率
    - Redis 命中率，如果项目能统计
    - MySQL 查询次数变化，如果项目能统计
    - MQ 消息消费成功率，如果项目能统计

7. 不要编造真实压测结果。
    - 用“待填写”或空表格占位。
    - 不要写虚假的 QPS、响应时间或优化百分比。

8. 新增第三阶段验收清单，至少包含：
    - 商品缓存命中
    - 不存在商品空值缓存
    - 热点商品击穿保护
    - 关注列表缓存命中
    - 添加关注缓存失效
    - 重复关注幂等
    - 通知入口幂等
    - MQ 消费者幂等
    - 限流生效
    - 价格刷新分页批处理
    - 单商品刷新失败不影响整体任务
    - README 和 performance-test.md 已更新

9. 输出：
    - 修改文件清单
    - 文档新增内容摘要
    - 是否有未完成项

请先检查现有 README 和 docs 目录结构，再按现有风格做最小修改。