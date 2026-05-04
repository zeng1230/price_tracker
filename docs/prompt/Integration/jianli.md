继续当前 Integration 上下文，不要修改核心业务逻辑。

当前项目已经完成：
1. README Integration 最终验收
2. docs/PROJECT_REVIEW.md
3. docs/INTERVIEW_QA.md

现在请基于当前真实实现，新增一个简历与面试交付文档：

docs/RESUME_DELIVERY.md

内容包括：

1. 简历项目名称
   给出 2 到 3 个可选名称，偏后端实习简历风格。

2. 简历项目描述
   输出 2 个版本：
    - 精简版：适合简历，一段 50 到 80 字
    - 完整版：适合简历项目经历，4 到 6 条 bullet

3. 技术栈写法
   按简历格式列出：
   Java 17、Spring Boot 3、MyBatis Plus、MySQL、Redis、RabbitMQ、JWT、Validation、Knife4j、JUnit

4. 项目亮点
   提炼 4 到 6 个亮点，要求体现：
    - 完整业务闭环
    - Redis 缓存与缓存防护
    - RabbitMQ 异步通知
    - 幂等设计
    - 端到端联调验证
    - 测试覆盖

5. 面试 30 秒介绍
   要求自然、可背诵。

6. 面试 2 分钟介绍
   要求能讲清：
    - 项目做什么
    - 核心业务链路
    - 为什么用 Redis
    - 为什么用 RabbitMQ
    - 怎么避免重复通知
    - 怎么证明系统跑通

7. 面试官追问索引
   列出 10 个最可能追问的问题，并指向 docs/INTERVIEW_QA.md 中对应问题编号。

约束：
- 不要修改业务代码
- 不要修改 Controller、Service、Mapper、Entity
- 不要虚构未实现功能
- 不要夸大为生产级系统
- 输出修改文件清单
- 说明如何使用该文档投递实习

完成后只运行文档检查或现有测试，不要做业务改动。