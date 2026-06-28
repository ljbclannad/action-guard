# 定义规范

## 目的

这份文档定义了 `action-guard` 第一版的 Action 定义 DSL。

这套 DSL 是有意收敛过的：

- 一个 Action 定义描述一条业务副作用流程
- Step 只按串行顺序执行
- 可靠性与治理能力优先于工作流表达能力

## 定义结构

一个 Action 定义包含：

- Action 元数据
- 执行策略默认值
- 有序的 Step 定义列表
- 可选的告警与补偿行为

示例：

```yaml
name: order-cancel-flow
description: Cancel order follow-up actions after order state update
version: 1
enabled: true
idempotency:
  scope: ACTION
  keyTemplate: "${bizKey}"
alerts:
  onWaitingManual: P1
  onCompensationFailure: P1
defaults:
  retry:
    maxAttempts: 5
    backoff:
      mode: EXPONENTIAL
      initialDelay: 5s
      maxDelay: 10m
  timeout: 10s
steps:
  - name: revoke-coupon
    stepType: HTTP_CALL
    target: coupon-service/revoke
    request:
      bodyTemplate:
        orderId: "${attributes.orderId}"
        userId: "${attributes.userId}"
    compensation:
      stepType: HTTP_CALL
      target: coupon-service/restore
      request:
        bodyTemplate:
          orderId: "${attributes.orderId}"
          userId: "${attributes.userId}"
  - name: notify-user
    stepType: MQ_MESSAGE
    target: topic.user.order-cancelled
    retry:
      maxAttempts: 10
      backoff:
        mode: FIXED
        delay: 30s
```

## Action 字段

### 必填字段

- `name`：全局唯一的 Action 定义名
- `steps`：有序且非空的 Step 列表

### 可选字段

- `description`：可读描述
- `version`：用于灰度与发布控制的定义版本
- `enabled`：是否允许新的 publish 使用该定义
- `defaults`：默认重试与超时策略
- `idempotency`：Action 级幂等策略
- `alerts`：Action 级告警策略

## Action 幂等

Action 级幂等用于防止重复发布请求生成多份可执行 Action。

字段：

- `scope`：`ACTION` 或 `DEFINITION_VERSION`
- `keyTemplate`：从请求上下文解析出的表达式

推荐的第一版行为：

- 如果重复发布请求解析出相同的幂等 key，应返回已有的 Action 实例标识
- 重复发布不得再创建新的活跃 outbox 记录

## Step 定义

每个 Step 都严格按列表顺序执行。

必填字段：

- `name`：在当前 Action 内唯一
- `stepType`：Handler 类型
- `target`：Handler 侧使用的目标标识或路由目标

可选字段：

- `description`
- `retry`
- `timeout`
- `idempotency`
- `request`
- `successResult`
- `failurePolicy`
- `compensation`
- `alerts`

## StepType

第一版建议内建一小组标准化的 `stepType`：

- `HTTP_CALL`
- `MQ_MESSAGE`
- `BEAN_INVOKE`
- `WEBHOOK`
- `IM_GROUP_CREATE`
- `IM_GROUP_INVITE`
- `IM_GROUP_MESSAGE_SEND`
- `NOTIFY_IN_APP_SEND`
- `NOTIFY_SMS_SEND`
- `NOTIFY_EMAIL_SEND`

适配模块可以扩展新的类型，但运行时契约应保持一致。

## StepType 注册

`stepType` 不只是 YAML 里的一个标签，它还是运行时查找真正执行器的 key。

注册规则：

- 每个 `stepType` 必须恰好映射到一个活跃 handler
- handler 可以由框架模块提供，也可以由业务模块提供
- 同一个 `stepType` 的重复注册必须导致启动失败
- 对于无法解析的 `stepType`，应尽可能在运行前校验阶段直接失败

模块归属示例：

- `MQ_MESSAGE` -> RabbitMQ 适配模块
- `KAFKA_MESSAGE` -> Kafka 适配模块（当前模块仍是占位保留，第一版不作为推荐接入路径）
- `IM_GROUP_CREATE` -> IM 适配模块
- `IM_GROUP_INVITE` -> IM 适配模块
- `IM_GROUP_MESSAGE_SEND` -> IM 适配模块
- `NOTIFY_IN_APP_SEND` -> 通知适配模块
- `NOTIFY_SMS_SEND` -> 通知适配模块
- `NOTIFY_EMAIL_SEND` -> 通知适配模块
- `BEAN_INVOKE` -> 本地业务应用模块

这样可以让一个 Action 定义跨多个模块组合，同时保持 DSL 稳定。

## Target 路由

`target` 用于标识 Handler 最终要访问的下游平台、provider 或逻辑路由。

示例：

- `IM_GROUP_CREATE` 配合 `target: wecom`
- `IM_GROUP_INVITE` 配合 `target: feishu`
- `NOTIFY_SMS_SEND` 配合 `target: aliyun-sms`
- `NOTIFY_EMAIL_SEND` 配合 `target: smtp`
- `MQ_MESSAGE` 配合 `target: topic.user.created`

推荐规则：

- `stepType` 定义能力契约
- `target` 选择该能力下的具体 provider 或目标地址

这样可以避免 DSL 膨胀成 `WECOM_GROUP_INVITE`、`ALIYUN_SMS_SEND` 这种强 provider 绑定的 StepType 名称。

## Step Handler 契约

每个已注册的 `stepType` 都应实现统一的执行契约。

建议形态：

```java
public interface ActionStepHandler {

    String stepType();

    StepExecutionResult execute(ActionStepContext context);
}
```

框架运行时负责编排，Handler 只负责执行具体副作用。

Handler 不应：

- 直接更新 Action 状态
- 直接操作 outbox 记录
- 自己决定全局编排状态流转

Handler 应该：

- 执行下游操作
- 返回结构化执行事实
- 暴露标准化失败信息
- 遵守超时与幂等约束

## Step 上下文

传递给 Handler 的执行上下文至少应包含：

- action identity
- action name and version
- business key
- action attributes
- step definition
- attempt number
- resolved idempotency key
- timeout metadata
- prior step outputs if enabled by runtime policy

DSL 本身不会直接暴露这整个结构，但模板渲染和 Handler 执行都会依赖这些运行时输入。

## 能力特定输入结构

第一版应尽量保持统一的运行时契约，同时允许不同能力拥有各自的请求载荷结构。

示例：

- `IM_GROUP_CREATE`：群名、owner、成员列表、可选头像或扩展元数据
- `IM_GROUP_INVITE`：群 id、成员列表、邀请人
- `IM_GROUP_MESSAGE_SEND`：群 id、消息类型、渲染后的内容
- `NOTIFY_IN_APP_SEND`：接收人 id 列表、模板 id、变量
- `NOTIFY_SMS_SEND`：手机号列表、签名、模板 id、变量
- `NOTIFY_EMAIL_SEND`：收件人、主题、正文或模板变量

框架应在执行前或执行时，把渲染后的请求快照持久化下来，以便治理侧能准确查看“到底发出了什么请求”。

## Step 结果语义

Handler 返回结果至少要让运行时区分：

- 成功
- 可重试失败
- 终态失败

结果对象还可以附带：

- output payload
- downstream receipt id
- normalized error code
- error message

这对重试策略、治理展示和审计质量都很重要。

## 请求映射

请求映射负责把 Action 上下文转换成 Step 输入。

建议可用的数据来源：

- `bizKey`
- `attributes.*`
- 如果已经持久化且明确开放，则允许读取前序 Step 输出
- 运行时元数据，例如 `actionId`

第一版建议只支持简单模板替换和 map 渲染，避免引入复杂脚本。

## 重试策略

重试既可以在 Action 默认层定义，也可以被具体 Step 覆盖。

字段：

- `maxAttempts`
- `backoff.mode`：`FIXED` 或 `EXPONENTIAL`
- `backoff.delay` 或 `backoff.initialDelay`
- `backoff.maxDelay`
- `retryOn`：可选错误码列表或异常分类列表

规则：

- Step 本地策略优先覆盖 Action 默认策略
- 如果 Step 没有本地策略，则回退到默认值
- 每一次重试都必须持久化当前尝试次数和 `nextRunAt`

## 超时策略

`timeout` 定义一次 Step 尝试允许执行的最长时间。

规则：

- 超时应视为一次失败尝试
- 超时失败仍然可以被分类为可重试
- 超时信息必须在 Step 历史和告警上下文中可见

## Step 幂等

Step 幂等用于防止因为派发失败或 worker 故障重放导致的重复副作用。

字段：

- `keyTemplate`
- `mode`：`REQUIRED` 或 `BEST_EFFORT`

规则：

- `REQUIRED` 表示如果无法解析出确定性幂等 key，Handler 必须拒绝执行
- 在可行情况下，Handler 应把幂等 key 继续透传给下游系统

## 失败策略

失败策略用于定义重试耗尽后的处理方式。

建议字段：

- `afterRetriesExhausted`：`WAIT_MANUAL`、`FAIL_ACTION` 或 `START_COMPENSATION`
- `manualReasonTemplate`

推荐默认值：

- 如果前面至少有一个已完成步骤配置了补偿，优先使用 `START_COMPENSATION`
- 否则使用 `WAIT_MANUAL`

## 补偿定义

每个 Step 都可以定义一个可选的补偿块。

补偿字段：

- `stepType`
- `target`
- `request`
- `retry`
- `timeout`

规则：

- 只有正向步骤成功完成后，补偿才有资格执行
- 补偿按已完成正向步骤的逆序执行
- 补偿拥有独立的重试与超时策略
- 补偿结果必须可持久化且可审计
- 补偿也必须能解析到一个已注册、可执行的 handler 契约

## 告警策略

告警策略既可以定义在 Action 级，也可以定义在 Step 级。

建议字段：

- `onWaitingManual`
- `onFinalFailure`
- `onCompensationFailure`
- `onHighLatency`

取值可以是 `P1`、`P2`、`P3` 这类严重级别。

## 运行时上下文

暴露给模板的运行时上下文建议包含：

- `actionId`
- `actionName`
- `bizKey`
- `attributes`
- `currentStep`
- `attempt`
- `publishedAt`

第一版应避免在模板中支持任意代码执行。

## 校验规则

框架在以下情况下应拒绝一个定义：

- `name` 为空
- `steps` 为空
- Step 名称重复
- 某个 StepType 所需的必填字段缺失
- 重试策略内部自相矛盾
- 补偿块结构非法
- 使用了未注册 Handler 的 StepType

启动期校验不仅要检查正向 `stepType`，还应检查所有启用定义中被引用的补偿 `stepType`。

## 版本规则

推荐的第一版行为：

- 新发布默认使用最新启用版本，除非明确指定版本
- 运行中的 Action 实例继续沿用自己启动时解析到的版本
- definition version 应写入 `action_instance`

## DSL 中明确不支持的特性

- 不支持并行步骤
- 不支持 joins
- 不支持嵌套子流程
- 不支持内嵌脚本
- 不支持执行过程中动态修改图结构
