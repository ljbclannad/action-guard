# 架构设计

## 目标

`action-guard` 提供了一种在本地事务提交后，可靠编排异步业务副作用的方式。

它的架构围绕一个核心保证展开：

`publish(action)` 绝不能出现“业务事务成功，但异步副作用被悄悄丢失”的状态。

## 架构原则

- 使用 outbox 模式作为唯一可靠的发布路径。
- 将可靠发布与消息执行语义解耦。
- 第一版只支持串行步骤。
- 将治理视为运行时的一部分，而不是后补的外围系统。
- 优先采用显式状态流转，而不是隐式的内存控制流。
- 明确区分业务事务成功与下游副作用成功。

## 分层架构

### 1. 能力层

这一层负责面向业务的执行能力。

职责：

- 定义能力域的 `stepType` handler
- 封装下游平台集成细节
- 暴露统一的执行 SPI 实现

示例：

- IM 协作能力
- 通知能力
- 后续可能扩展的存储或审批能力

这一层不应负责 outbox 一致性，也不应负责 MQ 消费语义。

### 2. Publish / Outbox 层

这一层负责可靠的 Action 发布和持久化的执行调度。

职责：

- 接收 `publish(action)` 请求
- 在同一事务内持久化 `action_instance` 和 `action_outbox`
- 解析 Action 定义
- 决定下一步应该发出什么执行任务
- 持久化 Action 和 Step 的状态流转

这一层是编排状态的事实来源。

### 3. 消息执行层

这一层负责异步投递和消费语义。

职责：

- 将 outbox 任务发布到 MQ
- 消费 MQ 消息
- 容忍重复消费
- 对重复执行尝试做去重或 fencing
- 调用运行时 Step 执行回调
- 协调 MQ ack、重试和死信行为

这一层把 at-least-once 投递语义转化为可治理的 Step 执行过程。

## 逻辑组件

### 1. 发布 API

业务应用通过 `ActionPublisher.publish(ActionRequest)` 发起调用。

职责：

- 校验请求结构
- 解析 Action 定义名
- 持久化初始 Action 实例状态
- 在同一本地事务里持久化 outbox 任务

这个 API 不应直接执行远程副作用。

### 2. 定义注册表

定义注册表负责从 YAML 或其他配置源加载 Action 定义。

职责：

- 按名称和版本解析 Action 定义
- 在启动或刷新时校验定义结构
- 向运行时暴露 Step 级执行元数据

### 3. 持久化层

持久化层显式存储运行时状态。

核心持久化实体：

- `action_instance`
- `action_step_instance`
- `action_outbox`
- `action_audit_log`

持久化层负责保证状态持久化，而不是做编排决策。

### 4. Dispatcher

`ActionOutboxRecoveryService` 负责轮询可执行的 outbox 记录；单条记录的投递统一交给 `ActionOutboxDispatcher`。

职责：

- 在集群部署下安全地 claim 记录
- 把已 claim 的任务交给消息生产者
- 在持久化写入发布结果后释放或推进任务状态

Dispatcher 是“持久待执行任务”和“MQ 投递”之间的边界。

首次发布的事务提交回调、步骤推进的即时投递、恢复扫描都复用这个入口：

- 发送前以候选记录的版本抢占 `CLAIMED`，抢占失败不发送
- 发送成功后保存 `DONE`；发送失败回退到 `NEW`，保留 `dispatchId`，累计失败次数
- 仅恢复扫描可以传入已确认超时的 `CLAIMED` 候选；尚未到期以及 `DONE`、`DEAD` 记录不发送
- 发送后的乐观锁冲突直接退出，避免覆盖其他节点已经推进的状态
- 发送成功但保存 `DONE` 发生其他存储异常时，异常向上传递，记录保留 `CLAIMED` 等待超时恢复

MQ 发送与数据库更新仍不是原子操作，恢复可能重复投递，消费端和业务 Handler 仍需遵守幂等契约。

### 4.1 消息生产者

消息生产者把已 claim 的 outbox 任务发布到配置好的 MQ 通道。

职责：

- 把 outbox 任务转换成传输消息
- 附带消息标识和幂等元数据
- 发布到 topic 或 queue
- 返回发送结果或抛出异常，由 `ActionOutboxDispatcher` 持久化发布状态

### 4.2 消息消费者

消息消费者从 MQ 接收执行消息，并触发 Step 执行。

职责：

- 反序列化 Action 执行消息
- 做重复消费检查
- 在需要时对重复执行做 fencing
- 调用运行时执行回调
- 应用 ack、重试或死信决策

这就是通常所说的“消息消费层”。

### 5. 运行时执行器

运行时执行器负责驱动单个 Action 实例按串行步骤推进。

职责：

- 加载当前 Action 和 Step 状态
- 定位下一个可执行步骤
- 调用正确的 Step Handler
- 应用重试策略
- 写入执行结果、下一次调度信息和状态流转
- 在需要时触发补偿或治理钩子

### 6. Step Handler

Step Handler 负责执行具体副作用，例如：

- HTTP 调用
- MQ 消息发布
- Spring Bean 方法调用
- webhook 回调

每个 handler 至少应支持：

- 幂等执行契约
- 超时契约
- 结构化结果分类

### 6.1 Step Handler SPI

运行时不应把业务步骤逻辑硬编码在内核里。

相反，它应通过统一 SPI 解析每一个步骤，例如：

```java
public interface ActionStepHandler {

    String stepType();

    StepExecutionResult execute(ActionStepContext context);
}
```

这个 SPI 契约至少要把三件事说清楚：

- handler 负责哪个 `stepType`
- 运行时会提供什么执行上下文
- handler 如何上报成功、可重试失败和终态失败

### 6.2 Handler 注册模型

Handler 应由不同模块提供，并在应用启动时完成注册。

在 Spring Boot 中，推荐模型如下：

1. 每个适配模块或业务模块暴露一个或多个 `ActionStepHandler` Bean
2. starter 收集所有 Bean 并组装成 `StepHandlerRegistry`
3. 运行时通过注册表完成 `stepType -> handler` 解析

这样一来，只要每个被引用的 `stepType` 恰好有一个已注册 handler，一个 Action 定义就可以跨多个模块。

示例：

- `MQ_MESSAGE` 由 `action-guard-adapter-rabbitmq` 提供
- `HTTP_CALL` 由核心 HTTP 适配模块提供
- `GROUP_INVITE` 由 IM 集成模块提供
- `BEAN_INVOKE` 由本地应用模块提供
- `NOTIFY_SMS_SEND` 由通知适配模块提供

### 6.3 注册表职责

注册表应负责：

- 按 `stepType` 建立索引
- 拒绝同一个 `stepType` 的重复注册
- 暴露正向执行的查询能力
- 如果补偿使用同一套或独立 SPI，也要暴露补偿执行查询能力
- 支持在启动时对已加载的 Action 定义做校验

### 6.4 运行时查找规则

当运行时走到某个 Step 时：

1. 从已解析的 Action 定义中读取 `stepType`
2. 到注册表中查找匹配的 handler
3. 构造 `ActionStepContext`
4. 调用 handler
5. 持久化返回的执行结果

如果 `stepType` 没有注册 handler，框架绝不能静默跳过该步骤。理想情况下应在启动期定义校验时就失败；如果做不到，也应该以确定性的方式让 Action 失败，并给出治理可见的明确错误。

### 6.5 传递给 Handler 的上下文

`ActionStepContext` 至少应提供：

- action instance identity
- definition name and version
- business key
- resolved attributes
- current step definition
- current attempt number
- idempotency key
- deadline or timeout metadata
- access to previous persisted step outputs if that feature is enabled

Handler 不应直接访问 outbox claim 细节等编排内部机制。
Handler 也不应负责 MQ ack 或重复消费处理。

### 6.6 执行结果契约

`StepExecutionResult` 应足够明确，以支持治理与重试：

- `SUCCESS`
- `RETRYABLE_FAILURE`
- `TERMINAL_FAILURE`

结果对象还应支持：

- 标准化错误码
- 可读错误信息
- 可选的结构化输出载荷
- 可选的下游回执或消息 id

这样既能把策略决策留在运行时层，又能让 handler 准确上报执行事实。

### 6.7 补偿 Handler 模型

第一版可以接受两种模型：

- 复用 `ActionStepHandler`，把补偿建模为另一种 Step 定义
- 定义独立的 `ActionCompensationHandler` SPI

当下游逆向动作与正向动作存在明显差异时，更推荐把补偿独立建模。

无论采用哪种方式，补偿注册契约都必须足够明确，并支持启动期校验。

### 6.8 多模块组合

一个 Action 可以跨多个模块组合 handler。

示例：

- 第 1 步 `IM_GROUP_CREATE` 来自 IM 适配模块
- 第 2 步 `IM_GROUP_INVITE` 来自同一个 IM 适配模块
- 第 3 步 `IM_GROUP_MESSAGE_SEND` 来自同一个 IM 适配模块
- 第 4 步 `NOTIFY_SMS_SEND` 来自通知适配模块

编排层并不关心 handler 属于哪个模块，它只关心：

- `stepType` 只被注册一次
- handler 契约得到满足
- 执行结果被可靠持久化

这就是框架能够支持“先发消息，再建群，再拉人进群”这类跨域业务流程、同时又不把运行时耦合到某一个领域模块的原因。

### 6.9 推荐能力模块

项目结构应该显式表达能力域边界。

第一版推荐的适配模块：

- `action-guard-adapter-rabbitmq`：MQ 发布 handler
- `action-guard-adapter-kafka`：Kafka 占位适配模块，当前不属于第一版推荐主路径
- `action-guard-adapter-im`：IM 协作 handler
- `action-guard-adapter-notify`：通知 handler

第一版推荐的模块与 StepType 对应关系：

- RabbitMQ: `MQ_MESSAGE`
- Kafka: `KAFKA_MESSAGE`
- IM: `IM_GROUP_CREATE`, `IM_GROUP_INVITE`, `IM_GROUP_MESSAGE_SEND`
- Notify: `NOTIFY_IN_APP_SEND`, `NOTIFY_SMS_SEND`, `NOTIFY_EMAIL_SEND`

这样可以让模块边界按集成领域划分，而不是被迫细化成“每个 StepType 一个模块”。

### 7. 治理服务

治理服务提供面向操作员的控制能力与可观测性。

职责：

- 列表化和查看 Action 实例
- 查看 Step 历史和失败原因
- 执行人工重试、跳过、取消和补偿操作
- 发布告警
- 为每次人工操作写入审计日志

## 端到端流程

### 1. 发布阶段

在调用方的本地事务内：

1. 更新领域数据
2. 插入初始状态为 `PENDING` 等值的 `action_instance`
3. 插入可派发任务 `action_outbox`
4. 提交事务

如果事务回滚，则 `action_instance` 和 `action_outbox` 都不应残留可见记录。

### 2. Outbox 派发阶段

提交之后：

1. dispatcher 轮询 `action_outbox`
2. dispatcher 通过 lease 或状态流转 claim 可执行记录
3. 把 claim 到的记录交给消息生产者
4. 消息生产者把执行消息发布到 MQ

集群安全性来自持久化 claim 语义，而不是单纯依赖内存锁。

### 3. MQ 消费阶段

MQ 投递之后：

1. consumer 接收执行消息
2. consumer 校验消息标识和重复消费保护
3. 对于不可执行的重复投递，直接 ack 而不重复执行 Step
4. 对于可执行投递，调用运行时执行器

at-least-once 投递是默认前提，重复消费安全是强制要求。

### 4. 执行阶段

对于每个被 claim 的 Action：

1. runtime 加载定义和实例状态
2. runtime 找到当前串行步骤
3. runtime 调用 Step Handler
4. runtime 持久化 Step 结果
5. runtime 决定推进到下一步还是安排重试

如果后面还有步骤，publish / outbox 层会生成下一份可执行任务，再由消息执行层异步投递。

### 5. 完成阶段

Action 最终会进入以下终态之一：

- `SUCCESS`
- `FAILED`
- `CANCELLED`
- `COMPENSATED`
- `IGNORED`

`FAILED` 表示自动推进已经停止，此时需要人工操作或补偿策略介入。

## 状态模型

建议的 Action 状态：

- `PENDING`: 已创建但尚未被 claim
- `DISPATCHING`: 已被 claim 且正在处理
- `WAITING_RETRY`: 等待下一次执行窗口
- `WAITING_MANUAL`: 自动执行停止，需要人工处理
- `COMPENSATING`: 正在执行补偿
- `COMPENSATED`: 补偿完成
- `SUCCESS`: 全部步骤执行完成
- `FAILED`: 不可恢复失败
- `CANCELLED`: 被人工终止
- `IGNORED`: 已发布但被明确选择不执行

建议的 Step 状态：

- `PENDING`
- `RUNNING`
- `SUCCESS`
- `WAITING_RETRY`
- `FAILED`
- `SKIPPED`
- `COMPENSATING`
- `COMPENSATED`

## 重试模型

重试作用在 Step 级，而不是把整个 Action 粗暴地整体重跑。

每个 Step 定义应声明：

- 最大尝试次数
- 退避策略
- timeout
- 可重试错误分类

在 worker 释放执行权之前，重试必须先把尝试次数和下次执行时间可靠写入持久化状态。

## MQ 消费语义

框架应默认假设 MQ 提供的是 at-least-once 投递语义。

这意味着同一条执行消息可能因为以下原因被投递多次：

- consumer 在 Step 执行后、ack 前崩溃
- broker 超时后重新投递
- ack 结果存在网络不确定性
- 显式重放或死信恢复

必须具备的保护措施：

- 稳定的执行消息 id
- 持久化消费记录或等价的 fencing 状态
- Step 级幂等 key
- 在调用 handler 之前先做终态重复检测

消息层应该保护运行时免于意外重复副作用，但 handler 契约本身仍必须支持下游幂等执行。

## 消息层职责

消息执行层应显式负责：

- 传输消息结构
- consumer group 策略
- 消费去重
- 延迟重试或重新投递策略
- 死信处理
- 面向治理操作的重放支持

这些关注点不应泄漏到能力模块中。

## 补偿模型

补偿是框架的一等能力。

它不是数据库回滚的替代品，而是针对已执行副作用的前向恢复机制。

补偿规则：

- 只有已经成功完成的步骤才允许补偿
- 补偿按步骤逆序执行
- 每一次补偿尝试都必须可靠持久化
- 补偿失败本身也可以进入重试或人工治理流程

## 告警模型

告警应在具有治理意义的事件上触发，例如：

- 最终重试耗尽
- Action 进入 `WAITING_MANUAL`
- 补偿失败
- dispatcher claim 卡顿超过阈值
- Step 延迟超过阈值

告警不能成为唯一事实来源；持久化状态和审计日志才是权威依据。

## 事务边界

这里有两个关键事务边界：

### 边界 A：业务提交

业务更新、Action 实例写入和 outbox 写入必须在一个本地事务里原子提交。

### 边界 B：运行时状态推进

每个 Step 执行结果都必须在 worker 释放执行权之前可靠提交。框架绝不能在远程副作用已经发生后，仍然依赖内存中的“未落库进度”。

## 第一版明确支持的能力

- 显式 Action 发布
- 基于 YAML 的 Action 定义注册表
- 串行 Step 执行
- 基于 Step Handler SPI 和注册表驱动的模块扩展
- 基于 outbox 的派发
- Step 重试与超时
- 内建补偿模型
- 告警与人工治理

## 第一版明确暂缓的能力

- 并行分支
- DSL 中的条件分支
- 跨多服务的 saga choreography
- 跨地域双活协同
- 可视化工作流编排
