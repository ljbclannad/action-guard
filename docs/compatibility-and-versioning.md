# Compatibility And Versioning

## Goal

这份文档回答 P2 阶段最容易影响开源接入体验的几个问题：

- Definition Version 怎么理解
- 升级时哪些兼容性需要优先保证
- 数据库表结构怎么演进
- `stepType` 扩展时怎样避免破坏兼容
- BOM 准备怎么管理

## 1. Definition Version Behavior

当前规则：

- Action 发布时，会解析到一个确定的 definition 内容
- 运行中的 Action 一旦开始执行，不应被后续 definition 修改直接改变语义
- 接入方应把“definition 变更”视为新版本演进，而不是覆盖正在运行实例的行为

当前仓库第一版更偏向“稳定 definition 内容 + 稳定 YAML 路径”的使用方式，还没有引入复杂的多版本中心化管理。

建议接入方遵循：

- 对已有线上流程做破坏性修改时，优先新增 definition 名或保留旧定义并灰度切流
- 不要在已有运行中 Action 尚未收敛时，直接替换旧 definition 的关键步骤语义

## 2. Upgrade Compatibility Strategy

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

## 3. Database Schema Evolution Strategy

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

## 4. StepType Compatibility Strategy

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

## 5. BOM Release Strategy

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

## 6. Current Compatibility Baseline

当前建议把以下内容视为优先稳定边界：

- `ActionPublisher` 调用方式
- `ActionStepHandler` 扩展点
- Notify / IM provider 路由模型
- `action_instance / action_step_instance / action_outbox` 的核心状态语义
- optimistic locking 的 `version` 语义
- recovery 与 compensation 的核心状态流转

## 7. Recommended Release Discipline

对外发布前建议至少检查：

1. Quick Start 是否仍可跑通
2. demo 默认链路是否仍可运行
3. Starter 默认配置项是否有新增或默认值变化
4. schema 是否出现破坏性修改
5. 关键 SPI 是否发生签名变化

如果上面任一项发生变化，应该同步更新：

- README
- Quick Start
- Starter Config
- 兼容性说明或 release note
