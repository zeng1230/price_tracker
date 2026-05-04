你负责 Price Tracker 的 PT-03-Product 任务，只处理商品模块，不要修改认证、关注、通知、定时任务模块。

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
1. 实现商品模块 entity、dto、vo、mapper、service、serviceImpl、controller
2. 支持商品新增
3. 支持商品详情查询
4. 支持商品分页查询
5. 支持商品修改
6. 支持商品逻辑删除
7. 接入 Redis 商品详情缓存
8. 更新商品和删除商品时清理缓存

接口必须包括：
- `POST /api/products`
- `GET /api/products/{id}`
- `GET /api/products?pageNum=1&pageSize=10&keyword=`
- `PUT /api/products/{id}`
- `DELETE /api/products/{id}`

商品表结构固定为：
tb_product
- id BIGINT 主键自增
- product_name VARCHAR(255) 非空
- product_url VARCHAR(500) 非空
- platform VARCHAR(50) 默认 amazon
- current_price DECIMAL(10,2)
- currency VARCHAR(10) 默认 USD
- image_url VARCHAR(500)
- status TINYINT 默认1
- last_checked_at DATETIME
- created_at DATETIME
- updated_at DATETIME

要求：
1. 使用 MyBatis Plus
2. 分页查询使用 MyBatis Plus 分页能力
3. 逻辑删除使用 `status` 字段
4. 商品详情优先查 Redis，没有再查数据库并回填缓存
5. 缓存 key 命名清晰，例如 `product:detail:{id}`
6. DTO 和 VO 命名清晰
7. 参数校验完整
8. 若仓库中已有基础设施类，请直接复用
9. 不要实现关注、价格历史、通知、价格刷新逻辑

完成后运行编译验证，并只汇报修改文件、执行命令、编译结果。