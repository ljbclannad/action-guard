# TODOLIST

## 项目摘要

`action-guard` 是一个基于 Outbox 的配置化异步 Action 编排与治理组件。

当前项目已经不再是“纯脚手架 + 占位代码”阶段，最小主链路的核心原型已经落地，主要包括：

- 多模块 Maven 结构与模块分层
- Action API、Definition、Step SPI 与核心状态模型
- 本地 YAML Action Definition 加载与校验
- Action 发布时写入 `action_instance`、`action_step_instance`、`action_outbox`
- MySQL Repository 基础实现
- RabbitMQ producer / consumer 主路径
- consume log、重复消费判断、基础 ACK / retry / dead-letter 策略
- 串行 Step Runtime 基础执行逻辑
- starter 自动装配与 demo 最小演示
- 核心设计文档初稿

当前项目仍未完成的关键部分主要集中在：

- 首次创建 Outbox 时同步推送消息的主路径已落地
- 多步串行 Action 的自动持续推进主路径已落地
- 失败后的真正重试调度主路径已落地
- 补偿主路径已落地，但超时、退避、补偿重试、告警等可靠性能力仍未进入主路径
- 能力层 IM / Notify 模块尚未建立
- 治理接口查询、基础写操作与补偿轨迹查询主路径已落地，补偿入口已接入真实补偿执行

这份文档用于同时服务两个目标：

- 作为后续架构落地的开发待办
- 作为阶段推进和优先级管理清单

---

## 当前结构

### 1. 能力层

- `capability-layer`
- 当前包含 `message-guard-alert-webhook`
- 后续需补 IM、Notify 等能力模块

### 2. Publish / Outbox 层

- `publish-outbox-layer`
- 当前包含 `api`、`core`、`starter`、`store-mysql`、`store-redis`
- 已具备定义加载、发布建模、状态落库与基础 Runtime 能力

### 3. 消息执行层

- `message-execution-layer`
- 当前包含 `adapter-rabbitmq`、`adapter-kafka`
- RabbitMQ 已有主路径原型，Kafka 仍基本处于薄适配状态

### 4. 治理交付层

- `governance-apps`
- `message-guard-ops-api`
- `message-guard-ops-web`

### 5. 示例层

- `examples`
- `message-guard-demo`

### 6. 依赖版本管理

- `message-guard-bom`

---

## 已完成项

- [x] 明确项目定位为开源组件，而非单业务内部框架
- [x] 明确第一版只支持串行步骤
- [x] 明确基于 Outbox 的可靠发布方向
- [x] 明确三层核心结构：能力层、Publish/Outbox 层、消息执行层
- [x] 明确治理层和示例层独立归组
- [x] 完成 `README.md` 中文化
- [x] 完成 `architecture / definition-spec / data-model / ops-governance` 文档初稿
- [ ] 完成本地开发约束文档 `docs/CLAUDE.local.md`
- [x] 完成私有 GitHub 仓库初始化与首次推送

---

## P0：最小可运行原型闭环补齐

目标：在现有原型基础上，补齐“本地定义 + MySQL 状态 + MQ 投递 + MQ 消费 + 串行步骤执行”的真正可持续闭环。

说明：当前 `P0` 的大部分基础实现已经落地，但仍有若干关键点只完成了“代码骨架”或“单次演示路径”，尚未达到稳定闭环标准。

### P0.1 Publish / Outbox 层

- [x] 定义 `ActionStepHandler` SPI
- [x] 定义 `ActionStepContext`
- [x] 定义 `StepExecutionResult`
- [x] 实现 `StepHandlerRegistry`
- [x] 在 `starter` 中收集并注册所有 Step Handler
- [x] 定义 `ActionDefinitionRegistry` 抽象
- [x] 保留本地 YAML 作为第一版默认 Definition 来源
- [x] 补充 Action Definition 启动时校验逻辑
- [x] 实现 `DefaultActionPublisher.publish(...)`
- [x] 定义 `action_instance`、`action_step_instance`、`action_outbox` 的 repository 接口
- [x] 用 MySQL 落地基础持久化实现，而不是内存占位
- [x] 实现 Action 创建时同步写入 `action_instance + action_outbox`
- [x] 实现首次创建 Outbox 时立即推送消息的主路径
- [x] 明确并落地 outbox 发布成功后的状态推进
- [x] 明确并落地 outbox 发布失败后的错误状态
- 当前规则：首次发送成功后 outbox 进入 `DONE`；首次发送失败后 outbox 进入 `DEAD` 并向上抛出异常
- [x] 明确并落地 outbox 发布失败后的重试策略
- 当前默认规则：`publishRetryMaxAttempts=1`，可通过配置提升；失败时在当前线程内立即重试，成功则 `DONE`，耗尽则 `DEAD`

### P0.2 消息执行层

- [x] 定义 Outbox 消息模型
- [x] 定义 MQ message key / message id 规则
- [x] 实现 RabbitMQ producer 主路径
- [x] 实现 RabbitMQ consumer 主路径
- [x] 实现消费后回调 Runtime 执行步骤
- [x] 定义重复消费判断逻辑
- [x] 落地 `action_consume_log`
- [x] 实现消费去重或执行 fencing
- [x] 定义 ACK / retry / dead-letter 基础策略
- [x] 将 RabbitMQ 投递接入 Action 创建主路径，而不是仅由 demo 手动触发
- [ ] 明确 Kafka 模块在第一版中的角色：占位保留、最小实现，还是补齐到可用标准

### P0.3 Runtime 串行执行

- [x] 实现按 `stepType` 查找 handler 的执行逻辑
- [x] 实现步骤成功后的基础状态推进
- [x] 实现 Action 完成后的终态推进
- [x] 实现失败时写入标准错误信息
- [x] 补充最小运行链路的核心测试
- [x] 实现多步串行 Action 的自动持续推进，而不是只执行当前 step
- [x] 实现步骤失败后的真正重试调度，而不是仅写入 `RETRYING`
- [x] 明确 step 重试次数与 action 状态之间的关系
- `ActionStepInstance.attemptCount` 表示当前 step 已执行失败/成功的累计尝试次数
- `ActionStatus.RETRYING` 仅表示“当前 step 已失败，且系统将继续发起下一次重试”
- 重试后若 step 成功，Action 应立即离开 `RETRYING`，转入 `DISPATCHING` 或 `SUCCESS`
- 重试耗尽后，Action 不再保持 `RETRYING`，而是进入明确终态 `FAILED`
- [x] 统一 `NEW / DISPATCHING / RETRYING / SUCCESS / FAILED` 等状态的状态机语义
- `NEW`：Action 已创建并完成落库，但尚未进入后续步骤推进结果判定
- `DISPATCHING`：当前 step 已成功，系统正在推进下一步或等待下一步被继续消费执行
- `RETRYING`：当前 step 已失败，且策略判定系统将继续发起下一次重试
- `SUCCESS`：所有 step 已成功执行完成，Action 进入终态
- `FAILED`：当前 step 重试耗尽或策略判定不再继续重试，Action 进入失败终态
- P0 允许迁移关系：`NEW -> DISPATCHING / RETRYING / FAILED`，`DISPATCHING -> RETRYING / SUCCESS / FAILED`，`RETRYING -> DISPATCHING / SUCCESS / FAILED`

### P0.4 示例层

- [x] 提供一个最小串行 Action 示例
- [x] 补一个本地运行说明
- [x] 调整 `demo`，展示 `publish -> MQ -> execute -> success` 的基础演示路径
- [x] 将 `demo` 改成更贴近真实闭环的运行方式，避免由 runner 手动查询 outbox 并直接调用 producer
- [x] 为 `demo` 补启动依赖说明，例如 MySQL / RabbitMQ 准备方式与最小配置

### P0.5 验证目标

- [x] 本地静态 YAML 可定义一个 Action
- [x] 发布后可落库生成 action / outbox 记录
- [x] Outbox 消息可投递到 RabbitMQ
- [x] MQ consumer 可消费并执行步骤
- [x] 单步成功时 Action 可进入 `SUCCESS`
- [x] 多步 Action 可在无需人工干预的情况下完整执行到终态
- [x] 失败场景可自动进入重试并在重试耗尽后进入明确终态
- [x] demo 可作为“最小真实闭环”而不是“原型演示链路”

---

## P1：能力扩展与治理增强

目标：在最小闭环补齐后，补齐首批能力集和核心治理能力。

### P1.1 能力层

- [ ] 新增 `message-guard-adapter-im`
- [ ] 实现 `IM_GROUP_CREATE`
- [ ] 实现 `IM_GROUP_INVITE`
- [ ] 实现 `IM_GROUP_MESSAGE_SEND`
- [ ] 新增 `message-guard-adapter-notify`
- [ ] 实现 `NOTIFY_IN_APP_SEND`
- [ ] 实现 `NOTIFY_SMS_SEND`
- [ ] 实现 `NOTIFY_EMAIL_SEND`
- [ ] 明确 `target` 路由规则和 provider 抽象

### P1.2 重试与补偿

- [ ] 实现步骤级 retry policy
- [ ] 实现 backoff 策略
- [ ] 实现 timeout 处理
- [x] 实现补偿执行主路径
- [x] 实现逆序补偿流程
- [x] 增加 action 级补偿开关控制
- 当前规则：YAML `compensationEnabled` 默认为 `false`，数据库 `action_governance_policy.compensation_enabled` 可按 `actionName` 覆盖 YAML 默认值
- 当前规则：仅 `FAILED / DEAD` 可进入补偿，状态迁移为 `FAILED / DEAD -> COMPENSATING -> COMPENSATED / DEAD`
- 当前规则：只补偿已成功 step，按 `stepIndex` 逆序执行；无 compensator 的成功 step 会跳过并继续
- [x] 持久化补偿轨迹
- 当前规则：每次补偿生成一个 `compensation_batch_id`，并向 `action_compensation_log` 追加 step 级结果记录
- 当前规则：补偿轨迹状态当前只使用 `SKIPPED / SUCCESS / FAILED`
- [x] 实现补偿失败后的治理状态
- 当前规则：任一 compensator 失败时，Action 进入 `DEAD`，并保留补偿失败信息

### P1.3 治理交付层

- [x] `ops-api` 改为真实数据查询
- [x] 提供 action 列表查询接口
- [x] 提供 action 详情接口
- [x] 提供 step 详情接口
- [x] 提供 message consume 详情接口
- [x] 提供手动重试接口
- [x] 提供跳过当前步骤接口
- 当前最小语义：`skip` 将当前 step 记为稳定成功态，并通过治理审计区分“人工跳过”而非真实执行成功
- [x] 提供取消 Action 接口
- [x] 提供触发补偿接口
- 当前规则：补偿接口已接入真实补偿执行，并受 YAML 默认值 + action 级治理策略覆盖控制
- [x] 提供补偿轨迹查询接口
- [x] 提供审计日志查询接口

### P1.4 告警与可观测性

- [ ] 标准化告警事件模型
- [ ] 接通 webhook 告警能力
- [ ] 记录 retries exhausted 告警
- [ ] 记录 compensation failed 告警
- [ ] 记录 dead-letter / consume failure 告警
- [ ] 暴露基础 metrics

---

## P2：组件化完善与开源准备

目标：让项目从“可运行原型”进入“可对外接入的开源组件”状态。

### P2.1 接入体验

- [ ] 梳理 Starter 默认配置项
- [ ] 提供最小接入文档
- [ ] 提供模块选择建议
- [ ] 提供 FAQ / 常见接入问题
- [ ] 提供示例配置模板

### P2.2 兼容性与版本化

- [ ] 明确 Definition Version 行为
- [ ] 明确升级兼容策略
- [ ] 明确数据库表演进策略
- [ ] 明确 StepType 扩展兼容策略
- [ ] 梳理 BOM 发布策略

### P2.3 测试与质量

- [ ] 为核心状态流转补系统测试
- [ ] 为 MQ 重复消费补回归测试
- [ ] 为补偿主路径补测试
- [ ] 为 IM / Notify 能力补模块测试
- [ ] 为 demo 增加冒烟验证

### P2.4 文档完善

- [ ] 统一核心设计文档的中文化或双语策略
- [ ] 增加快速开始文档
- [ ] 增加模块架构图
- [ ] 增加 stepType 扩展开发指南
- [ ] 增加治理操作指南

### P2.5 开源发布准备

- [ ] 检查是否存在本地开发约束文件泄漏风险
- [ ] 清理仅内部开发有意义的占位内容
- [ ] 补 License / 开源说明
- [ ] 补贡献说明文档
- [ ] 评估从 private 切到 public 的条件

---

## P3：分布式一致性增强

目标：让项目从“单组件可用的异步编排框架”提升到“多实例部署下更稳健的分布式编排组件”状态。

### P3.1 调度 Ownership 与接管

- [ ] 明确 dispatcher / consumer 的 ownership 语义
- [ ] 明确节点宕机后的调度接管策略
- [ ] 为延迟重试 / timeout 到期调度补恢复机制
- [ ] 为长时间未完成的调度任务补僵尸检测规则

### P3.2 Fencing 与并发保护

- [ ] 系统化梳理 action / step / outbox / compensation 的 optimistic locking 语义
- [ ] 为 step 推进补更明确的 fencing token 或 version guard
- [ ] 为补偿流程补并发保护，避免多个节点重复补偿
- [ ] 为治理写操作补并发保护，避免 retry / skip / cancel / compensate 冲突

### P3.3 幂等与最终一致性

- [ ] 明确 MQ 重复投递下的 step 幂等要求
- [ ] 明确 compensator 幂等要求
- [ ] 明确治理写操作的幂等语义
- [ ] 明确最终一致性边界，区分可短暂不一致与不可重复推进的状态

### P3.4 恢复与容灾

- [ ] 明确节点重启后的运行态恢复策略
- [ ] 明确补偿中断后的恢复策略
- [ ] 明确 outbox 未完成记录的恢复扫描策略
- [ ] 明确 action 长时间卡住时的自动恢复或人工接管策略

### P3.5 分布式测试与压测

- [ ] 为多实例并发推进补系统测试
- [ ] 为补偿并发冲突补回归测试
- [ ] 为 dispatcher / consumer 宕机恢复补测试
- [ ] 为重复消息 + 手工治理操作并发补测试
- [ ] 补一组最小压测或稳定性验证脚本

---

## 风险与约束

### 当前主要风险

- 当前最容易产生“看起来已闭环、实际上仍需人工触发”的错觉，尤其是 Outbox 首次发送与多步推进
- Runtime 已有基础状态流转，但失败重试、补偿和超时尚未形成完整可靠性语义
- 治理层与能力层尚未跟上核心原型进度，后续可能出现内核先行、外围长期缺位的问题
- 文档与实现已经比早期更接近，但仍需要持续同步，避免 `P0` 被误判为已完全完成

### 当前明确约束

- 第一版只做串行步骤
- Action Definition 默认使用本地静态 YAML
- 不绑定 Nacos 等动态配置中心
- 优先保证单组件可运行和外部接入便捷性
- 以开源组件标准约束模块边界和依赖方向

---

## 建议执行顺序

建议按以下顺序推进：

1. 先补首次创建 Outbox 时立即推送消息的主路径，让“发布落库”变成真正的“可靠投递”
2. 再补 Runtime 多步自动推进，确保串行 Action 不需要人工再次触发
3. 然后补失败重试调度与状态机语义，明确重试耗尽后的终态
4. 再把 demo 调整成真实闭环演示，并增加更强的验证用例
5. 最后再进入能力层、治理层、补偿与可观测性扩展
