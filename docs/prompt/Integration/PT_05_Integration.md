你负责 Price Tracker 的 PT-05-Integration 任务，只做整合、修复和校验，不要无故重写整个项目。

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
1. 检查整个仓库的模块整合情况
2. 统一包名是否都为 `com.example.price_tracker`
3. 检查 DTO、VO、entity 命名是否冲突
4. 检查 controller 路径是否符合要求
5. 检查 JWT 鉴权是否覆盖需要登录的接口
6. 检查 import、依赖、编译问题
7. 检查 Result、PageResult、ResultCode 的使用是否统一
8. 修复多窗口并行带来的冲突
9. 保持最小必要修改
10. 若可以，补充启动说明相关文档，例如 README 或简要启动指引

接口最终目标必须包括：

认证模块：
- POST /api/auth/register
- POST /api/auth/login
- GET /api/users/me

商品模块：
- POST /api/products
- GET /api/products/{id}
- GET /api/products
- PUT /api/products/{id}
- DELETE /api/products/{id}

关注模块：
- POST /api/watchlist
- GET /api/watchlist/my
- PUT /api/watchlist/{id}
- DELETE /api/watchlist/{id}

价格历史：
- GET /api/products/{id}/price-history

通知模块：
- GET /api/notifications/my
- PUT /api/notifications/{id}/read

内部接口：
- POST /api/internal/products/{id}/refresh-price

要求：
1. 优先做整合和修复
2. 不要推翻已有实现
3. 运行 `mvn -q -DskipTests compile`
4. 如果适合，再运行一次基础 smoke check
5. 最终尽量让项目达到可编译、可启动状态

完成后运行编译验证，并只汇报修改文件、执行命令、编译结果。