# Starter 配置说明

文档入口：[文档导航](../README.md)。

## 目的

这份文档汇总 `action-guard-spring-boot-starter` 当前已经生效的默认配置项，作为接入时的第一参考。

当前配置前缀统一为 `action.guard`。

## 存储实现选择

接入应用必须在自己的配置中显式声明 `action.guard.store.type`，没有默认值：

```yaml
action:
  guard:
    store:
      type: mysql
```

| 值 | 自动装配行为 | 前置条件 |
| --- | --- | --- |
| `memory` | 启用整套内存仓储，进程退出后数据丢失 | Starter 即可，不要求 MySQL 模块或数据源 |
| `mysql` | 启用 `store-mysql` 的 JDBC/MyBatis 仓储，不回退到内存 | 引入存储模块，配置数据源、对应驱动和事务管理器，并初始化表结构 |

未配置、空值或其他值会导致启动失败。选择 `mysql` 但缺少存储模块或数据源时，会报告对应缺失条件；驱动和连接问题由数据源初始化报告，不能以启动校验替代真实数据库联调。

模块依赖决定哪些实现可用，配置决定启用哪套实现。`memory` 不会禁用接入应用自身的数据源自动配置；即使数据源存在，框架也不会因此自动选择数据库仓储。现有自定义 Bean 覆盖机制保留，手工覆盖仓储时仍需自行保证整组实现和事务的一致性。

`mysql` 表示当前 JDBC/MyBatis 存储实现，不是对 JDBC URL 的数据库类型校验。默认演示已连接远程 MySQL，存储模块提供运行时 `com.mysql:mysql-connector-j`；H2 测试和显式 `h2` profile 同样配置 `mysql`。连接地址、账号和本地密码加载方式见 [demo 说明](../../examples/action-guard-demo/README.md)。

治理查询和审计接口仍依赖 JDBC 数据库，不随 `memory` 自动变成内存版；带治理接口的 demo 应继续使用 `mysql`。

## 执行结果事务接入

选择 `mysql` 时，存储自动配置在 Spring Boot 数据源自动配置之后、Starter 核心配置之前运行。存储模块复用已有 `ObjectMapper`，仅在缺少该类型 Bean 时创建默认实例；提供自定义实例会影响持久化 JSON 的序列化行为。

Starter 将可用的 `PlatformTransactionManager` 注入默认执行回调。Step 结果、Action 状态、迁移日志及后续 Outbox 在同一个独立短事务内提交，Handler 调用和 MQ 发送放在该事务之外。

- 数据库模式下，四类结果仓储必须使用同一数据源，并由所注入的事务管理器管理。多个事务管理器存在时，需要通过 `@Primary` 明确选择正确的管理器。
- 未配置事务管理器时，仅框架内置的全内存结果仓储组合允许启动；自定义或数据库结果仓储会在自动装配时报告缺少事务管理器。内存运行用于演示和测试，不提供数据库事务原子性。
- 单独使用 Core 或手动构造 `DefaultActionExecutionCallback` 时，数据库模式需使用接收 `Optional<PlatformTransactionManager>` 的构造器并传入管理器。旧的无管理器构造方式仅适用于内存运行。
- 调用方已有事务时，执行回调挂起它并独立提交执行结果，调用方随后回滚不会撤销执行结果。因此仅对已经提交的 Action 发起执行回调。
- 进程可能在结果提交后、消息发送前退出。需要持续恢复能力时启用恢复扫描；不将即时投递当作唯一保障。

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
- 作用：首次 outbox 发布到 MQ 时的同步最大尝试次数（包含首次发送，最小为 `1`）

当前语义：

- 每次发送前通过版本校验抢占为 `CLAIMED`，抢占冲突时退出
- 成功后 outbox 进入 `DONE`
- 发送失败后回退到 `NEW` 并累计失败次数；重试耗尽后告警，由恢复扫描继续补发
- 此配置仅控制首次发布；步骤推进的即时投递和每轮恢复对单条记录各尝试一次
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

- [可观测性说明](ops-governance.md#告警与指标)

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

- [快速开始](quick-start.md)
- [模块选择建议](quick-start.md#模块选择)
- [应用配置模板 YAML](../templates/action-guard-minimal-application.yml)
