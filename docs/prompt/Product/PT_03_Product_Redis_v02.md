窗口：Product

你刚刚已经确认：

1. PriceRefreshTask.java 位于 com.example.price_tracker.task
2. @EnableScheduling 位于 PraceTrackerApplication.java
3. PriceRefreshTask 调用 PriceService.refreshActiveProducts()
4. 价格刷新业务实现位于 PriceServiceImpl.refreshActiveProducts()

现在请不要修改 PriceRefreshTask.java，也不要修改 @EnableScheduling。

本窗口只负责优化 PriceServiceImpl.refreshActiveProducts() 的业务实现。

目标：
将当前价格刷新逻辑改造成第三阶段的批处理价格刷新能力。

要求：

1. 检查 PriceServiceImpl.refreshActiveProducts() 当前实现。
2. 将一次性查询全部商品的逻辑改为分页批处理。
3. batchSize 从配置读取，例如：
   price-tracker.price-refresh.batch-size
4. 每一页只处理固定数量的 active products。
5. 单个商品刷新失败不能中断整个任务。
6. 单个商品失败最多重试 2 次。
7. 每个商品刷新成功后：
    - 更新商品当前价格
    - 写入价格历史
    - 判断是否触发目标价通知
    - 如果触发，继续走已有 RabbitMQ 异步通知链路
    - 正确处理商品详情缓存和商品价格缓存失效
8. 增加任务级日志：
    - task start
    - batch start
    - scanned count
    - success count
    - failed count
    - notification triggered count
    - total cost
9. 增加测试：
    - 分页批处理能覆盖全部 active products
    - 单个商品失败不影响其他商品
    - 失败商品最多重试 2 次
10. 不要修改 PriceRefreshTask.java。
11. 不要修改 Foundation 通用基础设施。
12. 不要修改 WatchNotify 和 MQNotifyRefactor。
13. 完成后输出改动文件清单和测试结果。

注意：
如果当前分页查询 Mapper 方法不存在，可以在 Product 相关 Mapper 中补充必要查询方法。
如果当前缓存能力来自 Foundation，请复用现有 Cache Service 或 Redis Key 工具，不要重复造轮子。