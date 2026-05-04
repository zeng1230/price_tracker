窗口：WatchNotify

你现在在 Price Tracker 项目中工作，包名是 com.example.price_tracker。

第三阶段 Foundation 窗口已经提供统一 Redis Key、Cache Service、分布式锁、限流基础能力。
Product 窗口已经为商品详情和商品价格增加 Redis 缓存。

本窗口只负责关注与通知业务入口的高并发增强，不要重复创建 Redis 工具类。

任务目标：
为用户关注列表增加缓存，为关注操作增加幂等保护，并为目标价修改补充缓存失效逻辑。

要求：

1. 用户关注列表查询：
    - 优先读 Redis
    - 未命中查 MySQL
    - 查询后写入 Redis
    - TTL 使用 Foundation 提供的随机偏移能力

2. 关注列表缓存失效：
    - 添加关注后删除该用户关注列表缓存
    - 取消关注后删除该用户关注列表缓存
    - 修改目标价后删除该用户关注列表缓存

3. 关注幂等：
    - 同一用户重复关注同一商品不能插入重复记录
    - 优先检查数据库是否已有唯一约束
    - 如果没有，请补充 SQL 迁移说明或建表脚本修改建议
    - Service 层也要做重复判断，避免完全依赖数据库异常

4. 通知幂等：
    - 同一用户、同一商品、同一目标价条件下，短时间内不要重复触发大量通知
    - 使用 Foundation 的 Redis setIfAbsent 或幂等 key
    - 幂等 key TTL 从配置读取或给出合理默认值

5. 接口限流：
    - 对添加关注、取消关注、修改目标价接口接入 Foundation 的限流能力
    - 限流维度使用 userId + api
    - 不要在 Controller 里写重复限流逻辑

6. 日志：
    - watch list cache hit
    - watch list cache miss
    - watch list cache evict
    - duplicate watch ignored
    - notification idempotent hit
    - rate limited

7. 测试：
    - 关注列表缓存命中
    - 添加关注后缓存失效
    - 重复关注不会插入重复数据
    - 重复通知被幂等拦截
    - 超过限流次数会返回明确错误

8. 不要修改商品模块的缓存实现。
9. 不要修改 MQ 消费者实现，MQ 消费者幂等由 MQNotifyRefactor 窗口负责。
10. 完成后输出改动文件清单和测试结果。