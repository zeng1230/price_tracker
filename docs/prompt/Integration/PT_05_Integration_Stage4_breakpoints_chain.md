你现在接手 Price Tracker 项目的 Stage 4：Integration 系统集成。

请先阅读：
1. docs/CODEX_CONTEXT.md
2. docs/STAGE_HANDOFF.md
3. README.md
4. application.yml 或 application-dev.yml

然后扫描当前代码结构，确认文档是否与实际代码一致。

当前目标：
不要重新设计系统，不要重写已有模块。
请完成 Integration 阶段：

1. 找出 Product、Watch、Price、Task、Notify、RabbitMQ、Redis、MySQL 之间的集成断点
2. 修复最小必要断点
3. 跑通完整链路：
   用户登录
   → 创建商品
   → 关注商品并设置目标价
   → 触发价格刷新
   → 判断价格是否低于目标价
   → 发送 RabbitMQ 消息
   → 消费者消费通知消息
4. 补充 README 的本地启动步骤和联调步骤
5. 给出验证方式

约束：
- 不要引入微服务
- 不要引入前端
- 不要替换 RabbitMQ
- 不要大规模重构
- 优先保证本地可运行、链路可验证、README 可复现
- 每次修改后说明改了哪些文件、为什么改、如何验证

请先输出“集成断点清单”，然后开始修复。