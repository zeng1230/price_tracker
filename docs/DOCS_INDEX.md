# Price Tracker 文档索引

本页是 `docs/` 的交付入口。项目概览和最快启动路径优先阅读 README；需要复现实例时进入专项验收文档。

| 文档 | 用途 |
| --- | --- |
| [README](../README.md) | 项目定位、技术栈、架构、核心链路、数据模型、启动方式和能力边界 |
| [GitHub 参考项目分析](GITHUB_REFERENCE_ANALYSIS.md) | 外部参考仓库对比、可迁移思路、当前项目差距与批次建议 |
| [本地启动与验收](LOCAL_RUN_AND_ACCEPTANCE.md) | 从复制 `.env.example` 到 Flyway 建表、HTTP、MySQL、Redis、RabbitMQ 的完整可复制验收 |
| [Redis 验收](REDIS_ACCEPTANCE.md) | 每类 key 的触发方式、TTL、验证命令、预期结果和失败语义 |
| [RabbitMQ 验收](RABBITMQ_ACCEPTANCE.md) | 拓扑、正常消费、重试、DLQ、重复消息和 TraceId 验收 |
| [项目阶段总结](PROJECT_REVIEW.md) | 当前阶段的业务闭环、工程能力、已知限制和演进方向 |

## 辅助材料

| 文档 | 用途 |
| --- | --- |
| [Stage 5 交付证据](STAGE5_DELIVERY.md) | 既有阶段的编译、测试、全链路与中间件验证记录；其中历史边界描述可能早于当前 DLQ 配置，以本轮 README 和专项验收文档为准 |
| [SQL 索引说明](SQL_INDEX_EXPLAIN.md) | Flyway 管理的核心查询与复合索引设计 |
| [性能测试](performance-test.md) | 压测方案和观测指标 |
| [项目交接](STAGE_HANDOFF.md) | 既有交接与部署背景 |

## 推荐阅读顺序

1. [README](../README.md)：确认项目是什么、能做什么、不能做什么。
2. [本地启动与验收](LOCAL_RUN_AND_ACCEPTANCE.md)：跑通一条完整链路。
3. [Redis 验收](REDIS_ACCEPTANCE.md) 与 [RabbitMQ 验收](RABBITMQ_ACCEPTANCE.md)：验证中间件行为和失败语义。
4. [项目阶段总结](PROJECT_REVIEW.md) 与 [GitHub 参考项目分析](GITHUB_REFERENCE_ANALYSIS.md)：理解当前阶段和后续演进选择。
