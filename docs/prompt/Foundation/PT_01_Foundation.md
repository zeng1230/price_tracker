你负责 Price Tracker 的 PT-01-Foundation 任务，只处理基础设施，不要修改业务模块。

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
1. 初始化标准项目目录结构
2. 创建或完善 `pom.xml`
3. 创建或完善 `application.yml`
4. 新建数据库初始化 SQL 脚本
5. 创建 `common` 包
6. 创建 `config` 包
7. 创建 `exception` 包
8. 创建基础 `util` 包，但不要实现 JWT 主逻辑
9. 配置 MyBatis Plus 分页插件
10. 配置 Redis
11. 配置 Knife4j
12. 实现统一返回结构：
- `Result`
- `ResultCode`
- `PageResult`
13. 实现：
- `BusinessException`
- `GlobalExceptionHandler`

注意：
1. 不要生成 auth、user、product、watchlist、notification 的业务代码
2. 不要生成 JWT 拦截器主实现
3. 只做基础设施和底座
4. application.yml 中预留 jwt 配置项
5. SQL 脚本我已放在 `src/main/resources/sql` 
6. 若仓库为空，请从零创建最小可运行 Spring Boot 项目骨架

完成后运行编译验证，并只汇报修改文件、执行命令、编译结果。