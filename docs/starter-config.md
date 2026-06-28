# Starter 配置说明

## 目的

这份文档汇总 `action-guard-spring-boot-starter` 当前已经生效的默认配置项，作为接入时的第一参考。

当前配置前缀统一为 `action.guard`。

## 核心配置项

### `action.guard.definition-locations`

- 类型：`List<String>`
- 默认值：`classpath*:actions/*.yml, classpath*:actions/*.yaml`
- 作用：定义 Action YAML 的扫描位置

推荐做法：

- 单应用最小接入时，直接将 YAML 放在 `src/main/resources/actions/`
- 多模块项目时，保持公共规则在共享模块里，业务规则在业务模块里

### `action.guard.publish-retry-max-attempts`

- 类型：`int`
- 默认值：`1`
- 作用：首次 outbox 发布到 MQ 时的同步重试次数

当前语义：

- 成功后 outbox 进入 `DONE`
- 重试耗尽后 outbox 进入 `DEAD`
- 如果你已经启用恢复扫描，通常不建议把这个值调得过高

### `action.guard.metrics-enabled`

- 类型：`boolean`
- 默认值：`true`
- 作用：是否启用默认内存版 `ActionMetricsRecorder`

当前最小实现：

- 开启后会累计内存计数
- 关闭后 starter 会注入 no-op recorder

当前默认会累计的关键指标类别包括：

- 告警类计数，例如 `action.guard.retry.exhausted`
- 主链路结果计数，例如 `action.guard.action.succeeded`
- 治理操作计数，例如 `action.guard.governance.command`

完整指标语义见：

- [可观测性说明](/Users/lejinbo/LLM/action-guard/docs/observability.md)

## 恢复扫描配置项

### `action.guard.recovery.enabled`

- 类型：`boolean`
- 默认值：`false`
- 作用：是否启动统一恢复扫描器

建议：

- 单实例 demo 可以先不开
- 多实例或需要延迟重试/宕机恢复时建议开启

### `action.guard.recovery.batch-size`

- 类型：`int`
- 默认值：`100`
- 作用：每轮恢复扫描处理的最大记录数

### `action.guard.recovery.fixed-delay`

- 类型：`Duration`
- 默认值：`5s`
- 作用：恢复扫描固定间隔

### `action.guard.recovery.claim-timeout`

- 类型：`Duration`
- 默认值：`30s`
- 作用：`CLAIMED` outbox 超过这个时间未推进时，可被其他节点重新接管

### `action.guard.recovery.compensation-timeout`

- 类型：`Duration`
- 默认值：`1m`
- 作用：`COMPENSATING` action 超过这个时间未推进时，可被恢复扫描接管

### `action.guard.recovery.stuck-action-timeout`

- 类型：`Duration`
- 默认值：`5m`
- 作用：长时间停留在非终态 action 的僵尸检测阈值

当前检测范围：

- `NEW`
- `DISPATCHING`
- `RETRYING`
- `COMPENSATING`

## 相关外部配置

虽然不在 starter 自己的 `ActionGuardProperties` 里，但实际接入时通常需要同时配置：

- `spring.datasource.*`
- `spring.rabbitmq.*`
- `action.guard.rabbitmq.*`
- webhook 告警配置 `action.guard.alert.webhook.*`

推荐同时参考：

- [快速开始](/Users/lejinbo/LLM/action-guard/docs/quick-start.md)
- [模块选择建议](/Users/lejinbo/LLM/action-guard/docs/module-selection.md)
- [应用配置模板 YAML](/Users/lejinbo/LLM/action-guard/docs/templates/action-guard-minimal-application.yml)
