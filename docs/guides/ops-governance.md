# 治理与可观测性

文档入口：[文档导航](../README.md)。

## 目的

治理是框架的核心能力，不是一个可有可无的后台界面。

如果异步副作用可能失败、重复执行、触发补偿或长时间卡住，运维与业务操作人员就必须具备持久化可见性和有边界的控制能力。

## 当前状态

当前治理层已经在 `action-guard-ops-api` 中提供了真实可用的后端 API，用于：

- Action 列表查询
- Action 详情查询
- Step 详情查询
- 消费明细查询
- 执行 Outbox 投递诊断查询
- 告警 Outbox 投递诊断查询
- 审计日志查询
- 人工重试
- 跳过当前步骤
- 取消 Action
- 触发补偿入口

这不代表所有治理语义都已经完全成熟。

当前边界如下：

- 查询 API 返回的是运行时表中的真实数据，底层依赖已配置的 JDBC 存储
- 写操作会写入持久化治理审计日志
- 补偿入口已经接通真实的补偿运行时路径
- 补偿执行是否开启由 Action 级别开关控制，该开关由 YAML 默认值和数据库覆盖值共同决定
- 补偿执行会写入 Step 级别的持久化补偿日志
- 跳过能力目前采用最小语义：将当前 Step 置为稳定成功态，并通过审计日志区分“操作员跳过”和“真实执行成功”
- Action、Step 和 Outbox 的写入现在通过 `version` 共用统一的基于乐观锁的 fencing 规则
- 治理 HTTP API 必须通过接入方提供的身份解析 SPI 完成认证与授权；未提供身份解析器时默认拒绝访问

## 治理目标

- 让每个 Action 实例都可观测
- 让失败状态可诊断
- 让人工操作显式可见且可审计
- 防止不安全的人工干预
- 让自动化和人工干预共享同一套状态模型
- 让 MQ 重复消费变得可见且可解释

## 核心视图

运维治理层第一版应优先提供以下视图。

这些视图现在已经在 `action-guard-ops-api` 的 API 层实现。

### Action 列表

字段：

- action id
- action name
- biz key
- status
- 当前步骤
- 创建时间
- 更新时间
- 最后错误码
- 最后错误信息

常见筛选条件：

- status
- action name
- biz key
- 创建时间范围

当前实现说明：

- 已支持分页
- 已支持基础筛选
- 目前还不支持“仅查看等待人工处理”，因为当前运行时状态模型尚未暴露专门的 waiting-manual 终止路径

### Action 详情

字段：

- Action 基础字段
- 当前失败原因
- Step 摘要列表
- 消费摘要列表

当前实现说明：

- 当前还没有暴露已解析的 definition version
- 详情响应当前还不包含 outbox 状态
- Outbox 投递状态通过独立的 `GET /api/actions/{actionInstanceId}/outboxes` 查询，避免将消息投递状态与 Action 完成状态混为一谈
- 审计时间线通过单独的审计日志接口查询
- 补偿时间线通过单独的补偿日志接口查询

### Step 详情

字段：

- Step 名称和类型
- target
- 尝试次数
- 最后错误码和错误信息

当前实现说明：

- 当前响应模型还没有暴露 timeout、retry policy、请求/响应快照以及补偿历史

### 消息消费详情

字段：

- message id
- consumer group
- 消费状态
- 尝试次数
- 最近一次消费失败原因

当前实现说明：

- 当前使用 attempt count 作为 delivery 次数的近似代理
- 治理响应里还没有单独建模 dead-letter 状态

### Outbox 投递诊断

通过 `GET /api/actions/{actionInstanceId}/outboxes` 查询某个 Action 当前关联的 Outbox 记录。响应按 `createdAt`、`id` 升序返回，字段包括 `id`、`topic`、`dispatchId`、`status`、`availableAt`、`attemptCount`、`deliveryAttemptCount`、`version`、`createdAt` 和 `updatedAt`。

- `DONE` 表示消息发送成功且 Outbox 状态已落库，不代表 consumer 已处理消息或 Action 已进入 `SUCCESS`
- `NEW` 表示待投递记录；是否会在下一次扫描处理仍取决于 `availableAt`
- `CLAIMED` 是读取瞬间已被投递方抢占的状态，单次快照不能单独证明任务已卡死；恢复扫描会结合 `updatedAt` 和 claim timeout 判断能否接管
- `attemptCount` 是投递失败回退与业务重试调度都会累加的累计计数，不能解释为纯消息发送失败次数
- `deliveryAttemptCount` 才是仅消息发送失败次数；`version` 是乐观锁版本，不是重试次数

### 告警 Outbox 投递诊断

通过 `GET /api/actions/{actionInstanceId}/alert-outboxes` 查询关联 Action 的可靠告警记录。该接口属于 `/api/actions/**`，需要 `READ` 权限；按 `createdAt`、`id` 升序返回，不提供跨 Action 全局查询、人工重投、强制终止或状态修改。

响应包含 `id`、稳定的 `eventId`、类型、级别、Action/Step 关联、`status`、`availableAt`、`deliveryAttemptCount`、已脱敏的 `lastErrorMessage`、发生/创建/更新时间和 `version`。

- 告警记录在业务状态变更所在事务内写入；除相同业务事件的 `dedupeKey` 冲突外，写入或载荷序列化异常会使业务事务回滚
- `NEW → CLAIMED → DONE / DEAD` 是独立于执行 Outbox 的状态机，`deliveryAttemptCount` 只记录外部 sender 失败次数
- `DONE` 仅表示 `ActionAlertSender` 调用成功，不代表告警已被人工阅读
- sender 成功后若 `DONE` 落库失败，恢复扫描会允许再次发送，因此属于 at-least-once；接收端必须按 `eventId` 去重
- 未配置 sender 时仍会保存 `NEW` 记录，但不会主动投递；后续接入 sender 后可由恢复扫描继续处理

## 当前支持的人工操作

当前 API 暴露的人工操作集合不大，但约束比较严格。

### 人工重试

适用于底层问题已经被认为修复的场景。

规则：

- 只允许从 `FAILED` 或 `RETRYING` 进入
- 会写入持久化审计事件
- 复用现有的当前步骤派发路径

### 跳过当前步骤

仅当业务方接受省略该副作用时才应使用。

规则：

- 所有人工写操作均要求在 JSON 请求体中提供非空白 `reason`，并将其持久化到审计请求快照
- 当前项目阶段还不支持“不可跳过 Step”的元数据
- 会写入审计记录，并推进到下一步或 `SUCCESS`
- 当前实现会把被跳过的 Step 标记为稳定成功态，并依赖审计日志保留“操作员跳过”的语义

### 取消 Action

用于停止后续自动执行。

规则：

- 只允许对非终态 Action 执行
- 不会假装已经完成的副作用被撤销
- 当前会把 Action 状态置为 `IGNORED`

### 触发补偿

用于对已经成功执行过的前序步骤启动反向处理。

规则：

- 当前实现会校验 Action 状态并记录持久化审计
- 补偿是否开启的生效规则是：数据库中按 `actionName` 的覆盖优先，否则回退到 YAML 中的 `compensationEnabled`
- YAML 中 `compensationEnabled` 的当前默认值是 `false`
- 只有 `FAILED` 和 `DEAD` 能进入补偿流程
- 补偿只会针对已经成功执行的步骤运行
- 成功步骤会按 `stepIndex` 逆序补偿
- 如果某个成功步骤没有注册 compensator，会跳过该步骤并继续补偿
- 如果任一 compensator 执行失败，Action 会转为 `DEAD`
- 如果所有补偿都成功或可跳过，Action 会转为 `COMPENSATED`
- 一次补偿运行会生成一个 `compensation_batch_id`
- 每个被处理的成功步骤都会写入一条补偿日志

### 重新打开 Waiting Manual

用于在策略或数据修正后，把一个已人工处理的 Action 重新放回可派发执行状态。

## 安全控制

治理 API 必须通过服务端强制保证安全，而不能只依赖 UI 警告。

建议控制项：

- 对人工变更加上乐观锁版本校验
- 增加 Action 级和 Step 级的终态保护

当前实现说明：

- 已实现 Action 级状态校验
- 读接口需要 `READ` 权限；`retry`、`skip`、`cancel`、`compensate` 分别需要同名权限
- 接入方必须提供 `ActionOpsPrincipalResolver`，将已认证的 Spring Security、JWT、网关或 SSO 身份转换为 `ActionOpsPrincipal`；未提供或未认证时返回 `401`，权限不足时返回 `403`
- 框架不再信任任意请求 Header 作为操作人来源
- 所有人工写操作必须提供非空白 `reason`：HTTP 调用在 JSON body 中传递，缺失或空白时返回 `400`；直接 Java 调用必须使用 `ActionCommandService` 的三参数方法，传入 `null` 或空白时抛出
  `IllegalArgumentException`
- `reason` 校验发生在状态读取、幂等处理、Outbox 调度、补偿委托、审计和指标之前；无效原因表示命令未被接受，因此不会写入治理审计
- 目前还没有实现不可跳过 Step 标记
- 补偿还额外受到 Action 级治理开关保护
- 治理写冲突会显式暴露，而不是静默重试

## 告警与指标

### 告警能力

当前标准告警事件统一使用 `ActionAlertEvent` 建模，核心字段包括：

- `eventId`
- `type`
- `level`
- `title`
- `message`
- `actionName`
- `actionInstanceId`
- `stepName`
- `stepType`
- `occurredAt`
- `details`

当前主路径已接入的告警类型包括：

- `RETRIES_EXHAUSTED`
- `COMPENSATION_FAILED`
- `CONSUME_FAILURE`
- `DEAD_LETTER`
- `OUTBOX_PUBLISH_FAILED`
- `OUTBOX_DEAD`
- `ACTION_STUCK`

引入 `action-guard-alert-webhook` 并配置 `action.guard.alert.webhook.*` 后，webhook adapter 会提供 `ActionAlertSender`。它只进行一次 HTTP 外发，不自行重试或重新入队；网络、超时和非成功响应异常由告警 Outbox
状态机处理。webhook payload 包含稳定的 `eventId`，接收端必须据此去重。

`OUTBOX_PUBLISH_FAILED` 表示一次 dispatch 调用中的发送失败或即时重试耗尽，不代表执行 Outbox 已停止自动投递。`OUTBOX_DEAD` 仅在执行 Outbox 使用乐观锁成功转为 `DEAD` 后记录，表示 `deliveryAttemptCount`
已达到实际配置的最大投递次数；告警详情携带 Outbox、dispatch 和次数关联信息，可结合 `GET /api/actions/{actionInstanceId}/outboxes` 查询当前快照定位。

### 与事务的关系

告警事件在当前业务事务内先记录到 `action_alert_outbox`；正常写入失败或详情序列化失败会中止业务事务。重复的同一业务事件命中本地 `dedupeKey` 时返回已存在记录，作为幂等成功，不会回滚业务。

告警外发由独立恢复调度处理，状态机为 `NEW → CLAIMED → DONE / DEAD`。sender 失败时记录脱敏错误摘要、退避回到 `NEW`，达到最大次数进入 `DEAD`。为保证不丢失，sender 成功但完成状态未成功落库时允许重投；因此承诺是
at-least-once，而不是 exactly-once。指标仍维持提交后 best-effort，不会反向改变已经提交的业务状态。

`OUTBOX_DEAD` 采用“成功状态转换后、当前进程尝试一次”的 best-effort 语义：乐观锁确保同一次 `DEAD` 转换只有成功更新方尝试告警，但不保证 webhook 已送达；若 `DEAD` 落库后进程在通知前退出，仍可能漏报。跨重启、跨节点的可靠通知投递需要后续独立的持久化通知 Outbox 设计。

### 当前内建 metrics

当前默认指标模型以 counter 为主，由 `ActionMetricsRecorder.increment(...)` 统一承载。

starter 默认会注册内存版 recorder；如果你要接入 Micrometer、Prometheus 或公司内部平台，可以直接自定义 `ActionMetricsRecorder` Bean 覆盖默认实现。

#### 1. 告警类计数

- `action.guard.alert.published`
- `action.guard.retry.exhausted`
- `action.guard.compensation.failed`
- `action.guard.consume.failed`
- `action.guard.dead.letter`
- `action.guard.outbox.publish.failed`
- `action.guard.outbox.delivery.dead`
- `action.guard.action.stuck`
- `action.guard.outbox.recovery.succeeded`
- `action.guard.recovery.phase.failed`

#### 2. 运行结果计数

- `action.guard.step.succeeded`
- `action.guard.step.failed`
- `action.guard.step.timed_out`
- `action.guard.action.succeeded`
- `action.guard.action.failed`
- `action.guard.action.compensated`

#### 3. 治理操作计数

- `action.guard.governance.command`

用于统计 `RETRY / SKIP / CANCEL / COMPENSATE` 等治理命令的成功与失败次数。

### 当前 tag 语义

当前指标 tag 以低复杂度、可直接聚合为目标，主要包括：

- `actionName`
- `stepType`
- `result`
- `errorCode`
- `command`

并不是每个指标都会带所有 tag。

`action.guard.outbox.recovery.succeeded` 是当前 JVM 的成功恢复 Outbox 数量；`action.guard.recovery.phase.failed` 以有限的 `phase=outbox|compensation|stuck` 标记当前 JVM 的恢复阶段异常。它们都是进程内 counter，不能作为 Broker、数据库、其他节点或整个集群健康的结论。

例如：

- `action.guard.step.succeeded` 带 `actionName + stepType + result`
- `action.guard.action.failed` 带 `actionName + stepType + result + errorCode`
- `action.guard.governance.command` 带 `command + result`

### Micrometer / Prometheus 接入示例

如果你的应用已经使用 Spring Boot Actuator + Micrometer，可以直接用一个适配 Bean 把框架计数接进去：

```java
import io.github.actionguard.api.spi.ActionMetricsRecorder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.stream.Stream;
import org.springframework.context.annotation.Bean;

@Bean
ActionMetricsRecorder actionMetricsRecorder(MeterRegistry registry) {
    return (metricName, tags) -> Counter.builder(metricName)
            .tags(tags.entrySet().stream()
                    .flatMap(entry -> Stream.of(entry.getKey(), entry.getValue()))
                    .toArray(String[]::new))
            .register(registry)
            .increment();
}
```

最小依赖示例：

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

最小配置示例：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    prometheus:
      enabled: true
```

这样接入后，`action.guard.*` 计数会进入你的 Micrometer registry，并通过 `/actuator/prometheus` 暴露给 Prometheus 抓取。

### 当前边界

当前版本还没有内建：

- 标准化 timer / histogram
- Prometheus 指标暴露端点
- 预制 Grafana dashboard
- 多告警通道聚合编排

因此更准确的定位是：

- 框架已经提供主链路事件与计数语义
- 监控平台接入与展示层由使用方按自身环境接管

## 审计要求

每一次人工操作都必须生成不可变的审计事件。

必需的审计上下文：

- 谁发起了这次操作
- 操作发生的时间
- 影响到了哪个 Action 和 Step
- 请求内容是什么
- 状态发生了什么变化
- 为什么执行这次人工操作

当前实现说明：

- 已通过 `action_ops_audit_log` 实现持久化审计存储
- 当前存储字段包括 action id、operation type、operator、请求快照、结果状态、结果消息和创建时间
- 当前 `operator` 来自通过 `ActionOpsPrincipalResolver` 验证的 `ActionOpsPrincipal.operatorId`，不接受任意 Header 伪造身份
- 写操作请求快照至少保存 `reason`；取消和跳过还会记录状态迁移 event、fromStatus 与 toStatus
- 补偿成功与失败都走同一条治理审计链路

## 治理 API 范围

当前 API 分类：

- 列出 Action
- 查询 Action 详情
- 查询 Step 详情列表
- 查询消息消费详情列表
- 查询 Action 关联的 Outbox 投递诊断
- 人工重试
- 跳过 Step
- 取消 Action
- 触发补偿
- 查询补偿日志
- 列出审计日志
