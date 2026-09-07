# 版本演进与项目维护

文档入口：[文档导航](../README.md)。

## 目标

这份文档集中说明早期项目的契约演进、版本发布和文档维护：

- Definition Version 应该如何理解
- 升级时哪些兼容性需要优先保证
- 数据库表结构怎么演进
- `stepType` 扩展时怎样避免破坏兼容
- BOM 准备怎么管理

## 1. Definition Version 行为

当前规则：

- Action 发布时，会解析到一个确定的 definition 内容
- 运行中的 Action 一旦开始执行，不应被后续 definition 修改直接改变语义
- 接入方应把“definition 变更”视为新版本演进，而不是覆盖正在运行实例的行为

当前仓库第一版更偏向“稳定 definition 内容 + 稳定 YAML 路径”的使用方式，还没有引入复杂的多版本中心化管理。

建议接入方遵循：

- 对已有线上流程做破坏性修改时，优先新增 definition 名或保留旧定义并灰度切流
- 不要在已有运行中 Action 尚未收敛时，直接替换旧 definition 的关键步骤语义

## 2. 升级兼容策略

### 存储选择配置迁移

新增必填的 `action.guard.store.type` 是配置行为的破坏性变化。原先依赖自动装配与内存回退、未声明该属性的接入应用，升级后将启动失败，需要显式选择：

- 使用 JDBC/MyBatis 仓储的应用，包括 H2 demo：配置 `mysql`，保留存储模块、数据源和事务管理器。
- 仅使用内存仓储的测试或演示：配置 `memory`。
- 自定义仓储 Bean 的测试或应用：仍须声明选择；原有覆盖机制保留，手工组装的仓储和事务一致性由接入方负责。

该改动不改变表结构或持久化数据格式，也不会将内存或 H2 数据自动迁移到 MySQL。选择 `mysql` 后不再通过缺失 Bean 的内存回退补齐数据库仓储。示例和最小配置模板已同步声明 `mysql`。

### 通用策略

升级兼容优先级建议：

1. 公共 API / SPI 签名稳定
2. YAML 定义字段尽量向后兼容
3. 已持久化状态表字段尽量追加而非重写
4. 默认行为变化必须在文档中明确说明

当前仓库建议把变更分成三类：

- 兼容增强：新增配置、追加字段、增加新模块
- 受控变更：默认值调整，但旧配置仍可工作
- 破坏性变更：SPI 签名变化、状态语义变化、definition 关键字段语义变化

对于破坏性变更，建议：

- 明确记录在 release note
- 提供迁移说明
- 尽量避免与普通功能增强混发

## 3. 数据库 Schema 演进策略

当前数据库表演进建议：

- 优先追加列，而不是修改已有列语义
- 优先追加索引，而不是直接替换已有索引
- 保留已有状态字段和 version 字段语义稳定
- 通过 schema 脚本增量演进，避免运行中实例无法识别旧数据

当前默认演示使用：

- H2 file
- `MODE=MySQL`

生产切换可使用：

- MySQL

因此表结构演进要遵守一个现实约束：

- 新增 DDL 尽量保持 H2 MySQL mode 与 MySQL 双侧可执行

## 4. StepType 兼容策略

`stepType` 一旦被外部 YAML 使用，就应视为公开契约的一部分。

建议规则：

- 不要随意重命名已有 `stepType`
- 不要把 provider 名编码进标准 `stepType`
- 新能力优先新增 `stepType`，而不是复用旧 `stepType` 改语义
- 如果旧 `stepType` 已不推荐，先文档标记 deprecated，再提供迁移窗口

当前仓库推荐：

- `stepType` 表达能力类型
- `target` 表达 provider

这能最大限度降低 provider 迁移对 YAML 的影响。

## 5. BOM 发布策略

`action-guard-bom` 的目标是让接入方统一版本，而不是自己拼每个模块的版本号。

当前建议策略：

- 每次对外发布时同步发布 parent 和 BOM
- BOM 至少覆盖接入主路径常用模块
- 接入方优先 import BOM，再声明需要的模块依赖

当前仓库中的 BOM 还属于第一版，后续建议逐步覆盖：

- `action-guard-api`
- `action-guard-core`
- `action-guard-spring-boot-starter`
- `action-guard-store-mysql`
- `action-guard-adapter-rabbitmq`
- `action-guard-adapter-notify`
- `action-guard-adapter-im`
- `action-guard-alert-webhook`
- `action-guard-ops-api`

## 6. 当前兼容性基线

当前建议把以下内容视为优先稳定边界：

- `ActionPublisher` 调用方式
- `ActionStepHandler` 扩展点
- Notify / IM provider 路由模型
- `action_instance / action_step_instance / action_outbox` 的核心状态语义
- optimistic locking 的 `version` 语义
- recovery 与 compensation 的核心状态流转

## 7. 推荐发布纪律

对外发布前建议至少检查：

1. 快速开始是否仍可跑通
2. demo 默认链路是否仍可运行
3. Starter 默认配置项是否有新增或默认值变化
4. schema 是否出现破坏性修改
5. 关键 SPI 是否发生签名变化

如果上面任一项发生变化，应该同步更新：

- README
- 快速开始
- Starter 配置说明
- 兼容性说明或 release note

## 8. 公开与发布检查

项目仍处于 `early preview`，可评审、试用不等于全部模块已达到稳定生产承诺。内部设计可以按实际需求演进，涉及使用者与存量数据时说明迁移影响；不机械绑定未来版本号与尚未达到的成熟度。

首次公开仓库与每次版本发布分别检查：

| 场景 | 必须核对的内容 |
| --- | --- |
| 首次公开 | 私人配置、账号和本地辅助资料未进入公开内容；`.gitignore` 不能代替对发布内容的核查 |
| 首次公开 | README、License、贡献指南、安全说明、issue / PR 模板和 CI 入口齐全 |
| 每次发布 | 快速开始、配置、示例与实际行为一致；Kafka、Redis、治理应用等能力状态表述准确 |
| 每次发布 | 完成与变更风险相称的测试及 demo 验证，记录实际执行与跳过项；真实 MySQL / RabbitMQ 验证独立说明 |
| 每次发布 | API / SPI、YAML、默认值、Schema 和运行中实例的影响已说明，必要时提供迁移步骤 |
| 模块发布 | parent、BOM 与主路径模块版本一致，核对 BOM 实际覆盖范围 |

不对外发布 `docs/CLAUDE.local.md`、本地计划或私人开发辅助资料。公开仓库、提交、推送及发布操作仍需遵循项目授权规则；本清单不是执行这些操作的授权。

Release Note 使用简体中文说明发布目标、主要变化、兼容性影响、迁移方式和验证结果。变更分为兼容增强、默认行为调整与破坏性变化，避免将迁移风险隐藏在普通增强说明中。

## 9. 文档维护

- 正文、标题和导航使用简体中文；Maven 模块、Java 类型、配置键、SQL 名称和协议标识保留原文。
- 新内容优先补入已有主题，维护单一事实来源；示例使用当前实际支持的配置，规划单独标明。
- 链接使用仓库内相对路径，更新目录时检查 README、AGENTS、贡献指南和示例引用。
- 当前不维护全量中英双份文档。出现持续国际使用需求或稳定发布节奏后，再优先评估英文 README 和快速开始，避免历史文档整体翻译造成漂移。
