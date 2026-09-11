# 数据模型

文档入口：[文档导航](../README.md)。

阅读边界：本文包含模型建议和建议字段，实际表名、列与索引以 [当前 Schema](../../publish-outbox-layer/action-guard-store-mysql/src/main/resources/db/action-guard-mysql-schema.sql) 为准，本文不作为数据库迁移脚本。

## 初始化脚本与旧库升级

- 当前 Schema 用于初始化：索引与表一同定义在 `CREATE TABLE IF NOT EXISTS` 中，重复执行不会重复创建索引，也不清空已有数据。
- 新建的 `action_step_instance` 通过唯一索引 `uk_action_step_instance_action_step (action_instance_id, step_index)` 保证同一 Action 的一个步骤位置只有一条记录；不同 Action 可使用相同索引值。
- 脚本为全部表和字段提供中文 `COMMENT`。现有字段类型、默认值和其他索引保持不变，本次不增加扫描性能索引。
- **重复执行不等于升级旧表**：已有表不会自动获得新的唯一约束、字段或注释。旧库仍保留原有普通步骤索引，需单独评估迁移，不能仅凭初始化脚本执行成功判断结构已更新。
- 旧库升级步骤唯一约束前，先只读检查重复记录；若发现重复，确认业务数据处理方案后再迁移，不自动删除数据：

  ```sql
  select action_instance_id, step_index, count(*) as duplicate_count
  from action_step_instance
  group by action_instance_id, step_index
  having count(*) > 1;
  ```

- `MysqlSchemaInitializationTest` 使用 H2 的 MySQL 模式执行实际脚本，验证重复初始化保留数据、步骤唯一约束和表字段注释覆盖；该验证不替代真实 MySQL 初始化、注释元数据和旧库迁移验证。

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

当前 Schema 字段：

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
| `dispatch_id` | 逻辑投递标识；传输重发保持不变，步骤推进或业务重试时重新生成 |
| `status` | `NEW`、`CLAIMED`、`DONE`、`DEAD` |
| `available_at` | 最早可派发时间 |
| `attempt_count` | 投递失败回退与业务重试调度都会递增的累计计数，不是纯消息发送失败次数 |
| `delivery_attempt_count` | 仅消息发送失败次数，达到上限后 Outbox 进入 `DEAD` |
| `version` | 用于抢占和并发更新的乐观锁版本 |
| `created_at` | 创建时间 |
| `updated_at` | 最近一次变更时间；恢复扫描据此判断 `CLAIMED` 记录能否被接管 |

索引建议：

- 对 `(status, available_at)` 建查询索引
- 对 `(status, available_at, created_at)` 建恢复扫描索引
- 对 `action_instance_id` 建查询索引

规则：

- 插入 `action_outbox` 必须与 `action_instance` 在同一事务中完成
- 已被 `CLAIMED` 的记录在 `updated_at` 超过 claim timeout 后应允许重新 claim；当前 Schema 没有 `lease_owner` 或 `lease_expires_at`
- `DONE` 表示消息生产者已返回发送成功且发布状态已落库，不表示消息已被消费，也不等于整个 Action 已经成功完成
- `DEAD` 表示消息发送失败累计至实际配置的最大 `delivery_attempt_count` 后的投递终态；恢复扫描不会再次选中或重新发送该记录

当前实现中，三条投递路径发送失败时均在原有 `attempt_count` 与 `delivery_attempt_count` 上加一，成功发送不增加这两个值。当前表不保存最近投递错误、逐次投递历史或告警送达记录；需要这类信息时必须另行设计持久化事件、尝试日志或通知 Outbox，不能从当前快照推断。
步骤级业务重试调度仍沿用现有逻辑加一，因此该字段是累计计数，不应直接当作纯 MQ 发送次数；`delivery_attempt_count` 才是投递终态阈值的依据。

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
