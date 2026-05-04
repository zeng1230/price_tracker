窗口：Product

你现在在 Price Tracker 项目中工作，包名是 com.example.price_tracker。

第三阶段 Foundation 窗口已经提供了统一 Redis Key、Cache Service、分布式锁、限流基础能力。

本窗口只负责商品模块的高并发增强，不要重复创建 Redis 工具类，不要重复创建限流工具类。

任务目标：
为商品详情、商品当前价格查询增加 Redis 缓存，并补充缓存穿透、击穿、雪崩保护。

要求：

1. 检查 Foundation 提供的 Redis Key 管理类、Cache Service、Redis Lock 工具。
2. 商品详情查询：
    - 优先读 Redis
    - 命中则直接返回
    - 未命中则查询 MySQL
    - 查询成功后写入 Redis
    - TTL 使用 Foundation 提供的随机偏移能力

3. 商品当前价格查询：
    - 优先读 Redis
    - 未命中回源 MySQL
    - 回源后写 Redis

4. 缓存穿透保护：
    - 商品不存在时写入短 TTL 空值缓存
    - 后续查询同一不存在商品时不要再次访问 MySQL

5. 缓存击穿保护：
    - 热点商品缓存未命中时使用 Foundation 的分布式锁
    - 只有拿到锁的线程回源 MySQL
    - 未拿到锁的线程短暂等待后重试读取缓存

6. 缓存失效：
    - 商品价格更新后删除或更新商品详情缓存和商品价格缓存
    - 不要出现价格更新后接口仍长期返回旧数据

7. 日志：
    - cache hit
    - cache miss
    - null cache hit
    - lock acquired
    - lock failed
    - db fallback

8. 测试：
    - 商品详情缓存命中
    - 不存在商品第二次查询不访问 MySQL
    - 商品价格更新后缓存失效

9. 不要修改关注模块、通知模块、MQ 模块。
10. 完成后输出改动文件清单和测试结果。

请先分析商品模块现有 Service 和 Mapper，再做最小侵入式改造。