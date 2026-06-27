# action-guard

`action-guard` 是一个面向 Spring Boot 3 应用的、基于 Outbox 的异步 Action 编排与治理框架。

它面向这样一类业务场景：主交易流程必须保持简单，但交易完成后的副作用动作必须具备可靠性、可配置、可观测、可重试和可治理能力。

当前仓库状态：`early preview`。

这表示最小主链路、治理主路径和首批能力模块已经可用，但项目仍在继续收敛非主链路能力、观测接入形态和公开发布细节，不应直接理解为“完整生产级承诺”。

## 项目定位

`action-guard` 不是一个通用工作流引擎。

它聚焦于一个更窄但更常见的问题：

- 接收显式的业务 Action 发布请求
- 在同一本地事务内持久化 action 记录和 outbox 记录
- 异步分发后续执行任务
- 按严格串行顺序执行配置化步骤
- 在失败时提供重试、补偿、告警和人工介入能力

典型场景包括：

- 订单取消后的售后动作
- 支付完成后的通知链路
- 会员或优惠券撤销
- 账户状态向下游系统传播
- 一切“主交易成功后，异步副作用不能丢”的业务动作

## 核心能力

- 基于 Outbox 的可靠发布
- 基于 MQ 的异步步骤投递与执行
- 消费去重与重复消费治理
- 基于 YAML 的 Action 定义加载
- 严格串行步骤编排
- 多模块 Step Handler 扩展与注册
- 步骤级重试、超时、幂等与补偿契约
- Action 实例与 Step 实例持久化
- Dispatcher 轮询与 Claim 执行机制
- 告警与审计追踪
- 人工治理操作，如重试、跳过、取消和补偿

## 非目标

- 通用 DAG 工作流执行
- 第一版支持并行分支与汇聚
- 为下游系统制造“分布式事务已经解决”的假象
- 替代领域服务或 BPM 产品

## 运行链路

1. 业务代码调用 `publish(actionRequest)`。
2. 框架在同一本地事务内写入 `action_instance` 和 `action_outbox`。
3. Outbox 层调度待执行任务并发布到 MQ。
4. 消息执行层消费 MQ 消息，并进行重复消费保护。
5. Runtime 加载 Action 定义并执行当前步骤对应的 Handler。
6. 执行成功后推进到下一步，并在需要时继续发布下一条执行消息。
7. 执行失败后按策略进入重试、补偿、告警或人工介入流程。

## 阅读路径

面向框架使用方：

- [定义规范](./docs/definition-spec.md)
- [架构设计](./docs/architecture.md)
- [快速开始](./docs/quick-start.md)
- [Starter 配置](./docs/starter-config.md)
- [模块选择建议](./docs/module-selection.md)
- [模块架构](./docs/module-architecture.md)
- [常见问题](./docs/faq.md)
- [可观测性说明](./docs/observability.md)
- [兼容性与版本策略](./docs/compatibility-and-versioning.md)
- [发布纪律](./docs/release-discipline.md)
- [StepType 扩展指南](./docs/step-type-extension-guide.md)

面向框架维护者与平台开发者：

- [数据模型](./docs/data-model.md)
- [治理操作](./docs/ops-governance.md)
- [文档语言策略](./docs/documentation-language-strategy.md)
- [公开发布准备](./docs/public-release-readiness.md)

示例配置模板：

- [最小应用配置模板](./docs/templates/action-guard-minimal-application.yml)

开源协作说明：

- [CONTRIBUTING.md](./CONTRIBUTING.md)
- [SECURITY.md](./SECURITY.md)
- [LICENSE](./LICENSE)

## 最小使用模型

业务代码通过动作名称、业务主键和属性发布一个 Action：

```java
actionPublisher.publish(new ActionRequest(
        "order-cancel-flow",
        "order:12345",
        Map.of(
                "orderId", 12345L,
                "userId", 9988L,
                "refundId", "rf_001"
        ),
        List.of()
));
```

Action 定义决定事务提交后实际要执行什么：

```yaml
name: order-cancel-flow
description: Cancel order follow-up actions
steps:
  - name: revoke-coupon
    stepType: HTTP_CALL
    target: coupon-service/revoke
  - name: notify-user
    stepType: NOTIFY_SMS_SEND
    target: aliyun-sms
```

第一版只支持串行步骤。当前设计优先保证可靠性和治理能力，而不是追求工作流表达能力。

每个 `stepType` 都由一个已注册的 Step Handler 执行，这些 Handler 可以由框架模块或业务模块提供。例如，`MQ_MESSAGE` 可以来自 RabbitMQ 适配模块，`IM_GROUP_INVITE` 可以来自 IM 集成模块。

下面是一个跨多个能力模块的 Action 示例：

```yaml
name: onboarding-collaboration-flow
description: Create IM group, invite members, send group message, then notify user
steps:
  - name: create-group
    stepType: IM_GROUP_CREATE
    target: wecom
  - name: invite-members
    stepType: IM_GROUP_INVITE
    target: wecom
  - name: send-group-message
    stepType: IM_GROUP_MESSAGE_SEND
    target: wecom
  - name: send-sms
    stepType: NOTIFY_SMS_SEND
    target: aliyun-sms
```

## 分层结构

### 1. 能力层

负责业务能力本身及对应的 Step Handler。

- `action-guard-adapter-im`
- `action-guard-adapter-notify`
- 后续其它领域能力模块

### 2. Publish / Outbox 层

负责可靠发布语义和持久化调度。

- `action-guard-api`
- `action-guard-core`
- `action-guard-store-mysql`
- `action-guard-spring-boot-starter` 中与发布相关的部分

### 3. 消息执行层

负责 MQ 发布、MQ 消费、重复消费保护和步骤执行触发。

- `action-guard-adapter-rabbitmq`
- `action-guard-adapter-kafka`
- `action-guard-core` 中与消息执行相关的部分
- `action-guard-spring-boot-starter` 中与消息装配相关的部分

当前版本说明：

- RabbitMQ 是第一版唯一推荐的 MQ 主路径
- Kafka 模块当前仅占位保留，不作为第一版默认接入或验证目标

## 模块映射

- `action-guard-api`：公共请求模型、定义模型、SPI 契约
- `action-guard-core`：发布 Runtime、定义加载、编排原语、消息执行协调
- `action-guard-spring-boot-starter`：自动装配、注册表装配、Runtime 组装
- `action-guard-adapter-rabbitmq`：RabbitMQ 消息投递与消费适配
- `action-guard-adapter-kafka`：Kafka 占位适配模块，第一版不作为推荐接入组合
- `action-guard-adapter-im`：IM 协作能力适配，如建群、拉群、群发消息
- `action-guard-adapter-notify`：通知能力适配，如站内信、短信、邮件
- `action-guard-store-mysql`：Action 和 Outbox 状态的 JDBC 持久化实现，当前默认演示配置走 H2，线上可切换 MySQL
- `action-guard-store-redis`：锁、缓存与协同支持
- `action-guard-alert-webhook`：Webhook 告警通道
- `action-guard-ops-api`：治理后台 API
- `action-guard-ops-web`：治理控制台应用
- `action-guard-demo`：示例应用


