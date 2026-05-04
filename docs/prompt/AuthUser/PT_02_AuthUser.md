你负责 Price Tracker 的 PT-02-AuthUser 任务，只处理认证与用户模块，不要修改商品、关注、通知、定时任务模块。

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
1. 实现用户实体、DTO、VO、Mapper、Service、ServiceImpl、Controller
2. 实现注册接口
3. 实现登录接口
4. 实现获取当前登录用户信息接口
5. 接入 JWT 鉴权
6. 提供 `UserContext` 获取当前用户 id
7. 通过拦截器或过滤器校验 token
8. 密码使用 `BCryptPasswordEncoder` 加密存储
9. token 从请求头 `Authorization` 获取，支持 `Bearer xxx`

接口必须包括：
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/users/me`

用户表结构固定为：
tb_user
- id BIGINT 主键自增
- username VARCHAR(50) 唯一 非空
- password VARCHAR(100) 非空
- email VARCHAR(100)
- nickname VARCHAR(50)
- status TINYINT 默认1
- created_at DATETIME
- updated_at DATETIME

要求：
1. 使用 MyBatis Plus
2. 参数校验完整
3. 登录成功返回 token
4. 不要直接返回 entity
5. 若基础设施层已有 Result、BusinessException、配置类，请直接复用
6. 若 JWT 工具类不存在，可以在当前模块补齐：
    - `JwtUtil`
    - 鉴权拦截器或过滤器
    - `UserContext`
7. 如需修改 `WebMvcConfig` 或安全相关配置，请直接改仓库中的对应文件
8. 不要生成商品、关注、价格历史、通知代码

完成后运行编译验证，并只汇报修改文件、执行命令、编译结果。