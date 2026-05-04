你负责 Price Tracker 的 PT-04-WatchNotify 任务，只处理关注、价格历史、通知、价格刷新、定时任务模块，不要重新实现认证和商品基础 CRUD。

你现在正在当前仓库中参与 Price Tracker 项目的开发，请直接修改项目文件，不要只输出代码片段。

全局约束：
1. 项目包名统一使用 `com.example.price_tracker`
2. 项目为单体 Spring Boot 3.x 项目
3. Java 17
4. Maven
5. MySQL 8
6. MyBatis Plus
7. Redis
8. JWT
9. Lombok
10. Spring Validation
11. Knife4j
12. 所有时间字段使用 `LocalDateTime`
13. 所有金额字段使用 `BigDecimal`
14. 所有接口统一返回 `Result<T>`
15. DTO 负责请求参数
16. VO 负责响应参数
17. 不要直接向前端暴露 entity
18. controller 只做接参与返回
19. service 负责业务逻辑
20. mapper 使用 MyBatis Plus
21. 删除优先采用逻辑删除
22. 不要引入前端
23. 不要引入微服务
24. 如果发现已有代码与要求冲突，优先在当前仓库中做最小必要修改
25. 完成任务后运行 `mvn -q -DskipTests compile` 验证
26. 如果编译失败，优先继续修复可修复的问题
27. 最后只汇报：
- 修改了哪些文件
- 执行了哪些命令
- 当前编译是否通过
- 还剩什么阻塞点
  不要把所有文件完整打印在聊天里，除非我明确要求查看具体文件。

你的任务范围：
1. 实现 watchlist 模块
2. 实现 price_history 模块
3. 实现 notification 模块
4. 实现 `PriceService`
5. 实现 `refreshProductPrice(Long productId)`
6. 实现 mock 价格波动工具
7. 实现内部刷新单个商品价格接口
8. 实现定时批量刷新商品价格
9. 当价格达到目标价时生成通知
10. 基于 `last_notified_price` 做简单防重复提醒

涉及接口必须包括：

关注模块：
- `POST /api/watchlist`
- `GET /api/watchlist/my`
- `PUT /api/watchlist/{id}`
- `DELETE /api/watchlist/{id}`

价格历史：
- `GET /api/products/{id}/price-history`

通知模块：
- `GET /api/notifications/my`
- `PUT /api/notifications/{id}/read`

内部接口：
- `POST /api/internal/products/{id}/refresh-price`

相关表结构如下：

1. tb_watchlist
- id BIGINT 主键自增
- user_id BIGINT 非空
- product_id BIGINT 非空
- target_price DECIMAL(10,2)
- notify_enabled TINYINT 默认1
- last_notified_price DECIMAL(10,2)
- status TINYINT 默认1
- created_at DATETIME
- updated_at DATETIME
- 唯一索引 (user_id, product_id)

2. tb_price_history
- id BIGINT 主键自增
- product_id BIGINT 非空
- old_price DECIMAL(10,2)
- new_price DECIMAL(10,2) 非空
- captured_at DATETIME
- source VARCHAR(50) 默认 mock

3. tb_notification
- id BIGINT 主键自增
- user_id BIGINT 非空
- product_id BIGINT 非空
- watchlist_id BIGINT 非空
- notify_type VARCHAR(50) 非空
- content VARCHAR(500) 非空
- is_read TINYINT 默认0
- send_status TINYINT 默认0
- created_at DATETIME
- sent_at DATETIME

核心逻辑要求：
1. 用户可以关注商品并设置目标价
2. 查询我的关注列表需要分页
3. 修改目标价和提醒开关
4. 取消关注使用逻辑取消
5. 查询商品价格历史支持分页
6. 查询我的通知支持分页
7. 标记通知已读
8. `refreshProductPrice(Long productId)` 必须包含：
    - 查询商品
    - 获取旧价格
    - mock 生成新价格
    - 若价格变化则更新商品 current_price
    - 插入价格历史
    - 检查关注该商品的记录
    - 若达到目标价则生成通知
    - 基于 `last_notified_price` 防重
9. 提供定时任务，批量刷新有效商品价格
10. mock 价格逻辑放到 util 中，例如 `PriceMockUtil`

要求：
1. 可以依赖已有的 `UserContext`、`ProductMapper`、`Product`、`Result`、`PageResult`
2. 不要重新定义商品基础实体和商品 CRUD 控制器
3. 参数校验完整
4. 代码要可运行
5. 如需补充内部 controller、service 或 mapper，请直接写入项目

完成后运行编译验证，并只汇报修改文件、执行命令、编译结果。