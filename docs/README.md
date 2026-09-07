# 文档导航

当前维护 7 篇主题文档，本页作为统一入口。按任务选择阅读，不必通读整个目录。

| 文档 | 覆盖内容 |
| --- | --- |
| [快速开始](guides/quick-start.md) | 模块选择、接入步骤、示例验证和常见问题 |
| [配置与事务接入](guides/starter-config.md) | Starter 配置、结果事务、重试与恢复扫描 |
| [Action 定义与步骤扩展](guides/step-type-extension-guide.md) | 当前 YAML 字段、Handler / Sender、幂等与补偿扩展 |
| [治理与可观测性](guides/ops-governance.md) | 查询、人工操作、审计、告警、指标及监控接入 |
| [运行时架构](reference/architecture.md) | 模块职责、执行链路、状态推进和事务边界 |
| [数据模型](reference/data-model.md) | 表职责、状态与存储设计 |
| [版本演进与项目维护](maintenance/compatibility-and-versioning.md) | 兼容性、Schema 演进、公开与发布检查、文档语言 |

首次接入先看快速开始和配置，新增步骤看定义与扩展，排查运行问题看治理文档。项目仍在完善中，设计建议不等于已实现能力；实际字段核对加载器与 Schema。

配置模板：[最小应用配置](templates/action-guard-minimal-application.yml)。可运行示例：[action-guard-demo](../examples/action-guard-demo/README.md)。

协作规则见 [AGENTS.md](../AGENTS.md) 和 [贡献指南](../CONTRIBUTING.md)。文档中的项目命令默认从仓库根目录执行。

新增内容优先补到现有主题；同一规则只维护一处，其他文档通过相对链接引用。临时记录与本地辅助资料不加入公共阅读导航。
