# 数据模型

## 目标

数据模型必须让框架具备可恢复、可治理和集群安全的能力。

凡是对运维和治理有意义的状态流转，都必须可持久化、可查询。

## 核心表

第一版建议标准化以下核心表：

- `action_instance`
- `action_step_instance`
- `action_outbox`
- `action_audit_log`
- `action_consume_log`

后续可以按需补充辅助表，例如定义注册表缓存、告警投递记录或操作员身份信息表。

## action_instance

表示一次已发布的 Action 执行实例。

建议字段：

| Field | Purpose |
| --- | --- |
| `id` | 主键 |
| `action_name` | 定义名 |
| `definition_version` | 已解析的定义版本 |
| `biz_key` | 业务主键 |
| `status` | 当前 Action 状态 |
| `current_step_index` | 从 0 开始的当前步骤指针 |
| `total_step_count` | 从定义复制来的总步骤数，便于查询 |
| `retrying_step_index` | 当前正在重试的步骤索引 |
| `attributes_json` | Action 输入属性 |
| `idempotency_key` | 发布去重 key |
| `compensation_status` | 当前补偿阶段 |
| `next_run_at` | 下次可调度执行时间 |
| `last_error_code` | 标准化失败分类 |
| `last_error_message` | 失败原因摘要 |
| `created_at` | 创建时间 |
| `updated_at` | 最近一次状态更新时间 |
| `finished_at` | 进入终态的时间 |

索引建议：

- 在启用 Action 级去重时，对 `idempotency_key` 建唯一索引
- 对 `(status, next_run_at)` 建查询索引
- 对 `(action_name, biz_key)` 建查询索引
- 对 `created_at` 建查询索引

## action_step_instance

表示一个 Action 中每个 Step 的持久化执行状态。

建议字段：

| Field | Purpose |
| --- | --- |
| `id` | 主键 |
| `action_instance_id` | 所属 Action |
| `step_index` | 串行顺序索引 |
| `step_name` | 步骤标识 |
| `step_type` | Handler 类型 |
| `target` | Handler 目标 |
| `status` | 当前 Step 状态 |
| `attempt_count` | 正向执行总尝试次数 |
| `max_attempts` | 已解析后的最大重试次数 |
| `next_run_at` | 下次重试时间 |
| `timeout_ms` | 已解析后的超时时间 |
| `idempotency_key` | 已解析后的 Step 幂等 key |
| `request_payload_json` | 渲染后的请求快照 |
| `response_payload_json` | 可选的结果快照 |
| `last_error_code` | 标准化失败分类 |
| `last_error_message` | 失败原因摘要 |
| `started_at` | 当前或首次启动时间 |
| `finished_at` | 最终完成时间 |
| `updated_at` | 最近一次变更时间 |

索引建议：

- 对 `(action_instance_id, step_index)` 建唯一索引
- 对 `(status, next_run_at)` 建查询索引
- 对 `idempotency_key` 建查询索引

## action_outbox

表示可派发的运行时任务。

这张表是业务提交与异步执行之间的持久化桥梁。

建议字段：

| Field | Purpose |
| --- | --- |
| `id` | 主键 |
| `action_instance_id` | 所属 Action |
| `topic` | 逻辑任务类型，例如 `ACTION_EXECUTE` 或 `ACTION_COMPENSATE` |
| `status` | `NEW`、`CLAIMED`、`DONE`、`DEAD` |
| `available_at` | 最早可派发时间 |
| `lease_owner` | worker 或节点标识 |
| `lease_expires_at` | claim 过期时间 |
| `attempt_count` | 派发尝试次数 |
| `last_error_message` | dispatcher 层失败原因 |
| `created_at` | 创建时间 |
| `updated_at` | 最近一次变更时间 |

索引建议：

- 对 `(status, available_at)` 建查询索引
- 对 `lease_expires_at` 建查询索引
- 对 `action_instance_id` 建查询索引

规则：

- 插入 `action_outbox` 必须与 `action_instance` 在同一事务中完成
- 已被 claim 的记录在 lease 过期后应允许重新 claim
- `DONE` 表示消息生产者已返回发送成功且发布状态已落库，不表示消息已被消费，也不等于整个 Action 已经成功完成

当前实现中，三条投递路径发送失败时均在原有 `attempt_count` 上加一，成功发送不增加该值。
步骤级业务重试调度仍沿用现有逻辑加一，因此该字段是累计计数，不应直接当作纯 MQ 发送次数。

## action_consume_log

表示执行消息在消息层的消费状态。

这张表的存在，是为了让重复消费行为显式可见、可治理。

建议字段：

| Field | Purpose |
| --- | --- |
| `id` | 主键 |
| `message_id` | 稳定的 MQ 执行消息 id |
| `action_instance_id` | 所属 Action |
| `step_instance_id` | 可选的 Step 引用 |
| `consumer_group` | 逻辑消费者标识 |
| `consume_status` | `RECEIVED`、`EXECUTING`、`ACKED`、`DUPLICATE_SKIPPED`、`FAILED`、`DEAD_LETTERED` |
| `dedupe_key` | 重复消费 fencing key |
| `attempt_count` | consumer 侧重投次数 |
| `last_error_message` | 最近一次消费失败原因 |
| `first_received_at` | 首次收到消息时间 |
| `last_received_at` | 最近一次收到消息时间 |
| `updated_at` | 最近一次变更时间 |

索引建议：

- 对 `message_id` 建唯一索引
- 对 `(consume_status, last_received_at)` 建查询索引
- 对 `action_instance_id` 建查询索引
- 对 `dedupe_key` 建查询索引

规则：

- 一条执行消息必须拥有稳定的 `message_id`
- 重复投递应更新消费历史，而不是形成“看不见的重复行为”
- `DUPLICATE_SKIPPED` 必须可查询，以支持问题诊断

## action_audit_log

表示治理意义上的审计事件。

建议字段：

| Field | Purpose |
| --- | --- |
| `id` | 主键 |
| `action_instance_id` | 所属 Action |
| `step_instance_id` | 可选的关联 Step |
| `event_type` | 事件类别 |
| `event_source` | `SYSTEM`、`OPERATOR`、`DISPATCHER`、`RUNTIME` |
| `operator_id` | 可选的人类操作员标识 |
| `message` | 可读摘要 |
| `details_json` | 结构化事件载荷 |
| `created_at` | 事件时间 |

典型事件：

- published
- claimed
- mq_published
- consume_received
- consume_duplicate_skipped
- step_started
- step_succeeded
- retry_scheduled
- retries_exhausted
- entered_waiting_manual
- compensation_started
- compensation_succeeded
- operator_retry
- operator_skip
- operator_cancel

## 状态流转约束

`action_instance.status` 应只允许沿着明确的路径流转。

示例：

- `PENDING -> DISPATCHING`
- `DISPATCHING -> SUCCESS`
- `DISPATCHING -> WAITING_RETRY`
- `DISPATCHING -> WAITING_MANUAL`
- `WAITING_MANUAL -> DISPATCHING`
- `WAITING_MANUAL -> CANCELLED`
- `DISPATCHING -> COMPENSATING`
- `COMPENSATING -> COMPENSATED`
- `COMPENSATING -> WAITING_MANUAL`

仅靠 schema 本身无法完全约束这些路径，但运行时和治理 API 必须做到这一点。

## 消息消费状态约束

`action_consume_log.consume_status` 应遵循有边界的状态流转，例如：

- `RECEIVED -> EXECUTING`
- `EXECUTING -> ACKED`
- `RECEIVED -> DUPLICATE_SKIPPED`
- `EXECUTING -> FAILED`
- `FAILED -> RECEIVED`
- `FAILED -> DEAD_LETTERED`

在运行时结果和消费状态没有可靠落库之前，消息层不能把消息当作成功消费而提前 ack。

## 定义持久化策略

第一版可以接受两种模式：

- 采用文件定义，并把已解析版本复制到运行时表中
- 之后再引入持久化定义注册表

对于初始版本，只要满足以下条件，文件定义就是可接受的：

- Action 实例会存储已解析的 definition version
- 渲染后的 Step 元数据会快照到 `action_step_instance`
- 运行中的实例不依赖可变的 YAML 文件内容

## 保留与归档

建议生命周期：

- 活跃表保留近期运维数据
- 已完成的历史记录按时间窗口归档
- 审计日志的保留时间应长于热点 outbox 数据

归档流程至少要保留以下问题的可追溯能力：

- 当时运行了哪个 Action
- 实际执行了哪些步骤
- 为什么失败或进入补偿
- 执行过哪些人工操作
