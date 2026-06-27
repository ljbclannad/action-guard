# Module Selection

## Purpose

这份文档帮助接入方快速判断应该引入哪些模块，而不是一开始把所有模块都带上。

## Minimal Closed Loop

如果你的目标只是跑通最小真实主链路，建议：

- `action-guard-spring-boot-starter`
- `action-guard-adapter-rabbitmq`
- `action-guard-store-mysql`，默认先配 H2 文件库

再加上一种实际 step 执行能力：

- `action-guard-adapter-notify`
- 或业务自己实现 `ActionStepHandler`

## If You Need Notify

通知能力建议增加：

- `action-guard-adapter-notify`

适用 stepType：

- `NOTIFY_IN_APP_SEND`
- `NOTIFY_SMS_SEND`
- `NOTIFY_EMAIL_SEND`

## If You Need IM Collaboration

IM 能力建议增加：

- `action-guard-adapter-im`

适用 stepType：

- `IM_GROUP_CREATE`
- `IM_GROUP_INVITE`
- `IM_GROUP_MESSAGE_SEND`

## If You Need Governance APIs

如果你要查询 action、查看步骤、执行 retry/skip/cancel/compensate，建议再引入：

- `action-guard-ops-api`

## If You Need Alert Delivery

如果你要把告警推到外部系统，建议增加：

- `action-guard-alert-webhook`

## If You Need Custom Business Steps

如果你的步骤不是 IM / Notify 这种通用能力，而是业务自定义动作：

- 保留 `starter + rabbitmq + store-mysql`
- 本地演示默认使用 H2；线上再按需切换到 MySQL
- 在业务应用内直接定义 `ActionStepHandler` Bean

这是第一版最推荐的扩展方式。

## Current Non-Recommendation

当前阶段不建议默认引入：

- `action-guard-adapter-kafka`
- `action-guard-store-redis`

原因：

- Kafka 在第一版中明确按占位保留处理，不补齐为推荐可用标准
- Redis 协同能力尚未成为当前最小闭环的必需项

如果你的目标是当前版本真实落地，请不要把 Kafka 作为默认 MQ 选型。
