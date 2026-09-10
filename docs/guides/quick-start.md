# 快速开始

文档入口：[文档导航](../README.md)。

## 目标

这份文档面向第一次接入 `action-guard` 的 Spring Boot 3 应用，目标是让你用最少的步骤跑通一条真实链路：

- 发布 Action
- 写入 H2 或 MySQL 兼容存储状态
- 发布到 RabbitMQ
- 消费并执行 step
- 最终进入 `SUCCESS`

## 1. 添加依赖

### 模块选择

| 需求 | 模块或实现 |
| --- | --- |
| 默认执行主链路 | `action-guard-spring-boot-starter`、`action-guard-adapter-rabbitmq`、`action-guard-store-mysql` |
| 自定义业务动作 | 在应用中实现 `ActionStepHandler` Bean |
| 站内信、短信、邮件 | `action-guard-adapter-notify`，并提供相应 Sender Bean |
| IM 群创建、邀请、群消息 | `action-guard-adapter-im`，并提供相应 Sender Bean |
| 状态查询与人工处理 | `action-guard-ops-api`；独立入口由 `action-guard-ops-web` 提供 |
| 外部告警 | `action-guard-alert-webhook` |

默认以当前服务器的 MySQL 加 RabbitMQ 演示，连接和密码配置见 [demo 说明](../../examples/action-guard-demo/README.md)。Kafka、Redis 当前不作为推荐主路径，选型时先核对实际实现。引入能力模块并不等于已经接入真实厂商服务。

最小可运行组合建议：

```xml
<properties>
  <action-guard.version>0.1.0</action-guard.version>
</properties>

<dependencies>
  <dependency>
    <groupId>io.github.ljbclannad.actionguard</groupId>
    <artifactId>action-guard-spring-boot-starter</artifactId>
    <version>${action-guard.version}</version>
  </dependency>
  <dependency>
    <groupId>io.github.ljbclannad.actionguard</groupId>
    <artifactId>action-guard-adapter-rabbitmq</artifactId>
    <version>${action-guard.version}</version>
  </dependency>
  <dependency>
    <groupId>io.github.ljbclannad.actionguard</groupId>
    <artifactId>action-guard-store-mysql</artifactId>
    <version>${action-guard.version}</version>
  </dependency>
</dependencies>
```

如果你的 action 需要短信、邮件、IM 等能力，再按需追加能力模块。

## 2. 准备基础设施

当前最小主链路需要：

- H2 文件库或 MySQL
- RabbitMQ，当前服务器使用 4.x

演示的远程连接地址、账号和密码只从本地私密配置或环境变量读取，不随仓库发布。参考 [最小配置模板](../templates/action-guard-minimal-application.yml) 配置 `MYSQL_*` 与 `DEMO_RABBITMQ_*`。

应用侧至少要准备：

- `action.guard.store.type`：H2 / MySQL JDBC 存储选择 `mysql`；仅内存运行选择 `memory`
- `action.guard.execution.transport=rabbitmq`：显式启用框架默认 RabbitMQ 执行链路
- `spring.datasource.*`
- `spring.rabbitmq.*`

## 3. 添加应用配置

接入项目必须显式设置 `action.guard.store.type`，框架不再根据依赖或数据源自动推断存储方式：

```yaml
action:
  guard:
    store:
      type: mysql
    execution:
      transport: rabbitmq
```

该值选择 JDBC/MyBatis 仓储，H2 测试同样适用。存储模块已提供运行时 `mysql-connector-j`，默认配置 `com.mysql.cj.jdbc.Driver` 和当前服务器连接信息。纯内存测试可配置 `memory`，不具备持久化保证。

仅引入 RabbitMQ 适配器或配置连接不会启用默认执行生产者和消费者，必须显式选择 `rabbitmq`。示例拓扑由应用配置提供并按同一选择条件启用，用户自定义拓扑需自行添加启用条件。未配置时不影响其他业务使用 Spring
Boot 的 `RabbitTemplate`，自定义消息生产者仍可接入；没有生产者时 Outbox 不发送，也不会自动本地执行。该选择仅支持 `rabbitmq`（忽略大小写、不去除首尾空格），空值及其他值非法；缺少适配器或 `RabbitTemplate`
会启动报错，但配置校验不做网络探活。完整边界见 [执行传输选择](starter-config.md#执行传输选择)。

可以直接从模板复制：

- [action-guard-minimal-application.yml](../templates/action-guard-minimal-application.yml)

starter 默认配置说明见：

- [Starter 配置说明](starter-config.md)

## 4. 添加一个 Action 定义

在 `src/main/resources/actions/` 下新增一个 YAML，例如：

```yaml
name: order-cancel-flow
description: demo action
steps:
  - name: send-user-sms
    stepType: NOTIFY_SMS_SEND
    target: mock-sms
```

## 5. 提供 Step Handler 或能力适配模块

`action-guard` 不会凭空执行业务副作用。

你需要满足两种之一：

- 引入现成能力模块，例如 `action-guard-adapter-notify`
- 自己提供 `ActionStepHandler` Bean

如果使用通知模块，还需要提供对应 provider Bean，例如：

```java
@Bean
NotifySmsSender mockSmsSender() {
    return new NotifySmsSender() {
        @Override
        public String provider() {
            return "mock-sms";
        }

        @Override
        public NotifySendResult send(NotifySmsRequest request) {
            return NotifySendResult.succeeded();
        }
    };
}
```

## 6. 发布一个 Action

业务侧最小调用方式：

```java
actionPublisher.publish(new ActionRequest(
        "order-cancel-flow",
        "order:12345",
        Map.of("operator", "demo"),
        List.of()
));
```

## 7. 验证结果

最小成功标准：

- `action_instance` 存在记录
- `action_step_instance` 存在 step 记录
- `action_outbox` 从 `NEW / CLAIMED` 推进到 `DONE`
- MQ consumer 成功执行 step
- 最终 `action_instance.status = SUCCESS`

## 常见问题

- **为什么测试中还有 H2？** 当前通过 JDBC / MyBatis 持久化，单元测试及隔离验证使用 H2 的 MySQL 兼容模式；H2 测试通过不代表真实 MySQL 已验证。
- **`stepType` 与 `target` 怎么区分？** 前者是能力类型，后者是 provider 或业务路由目标；内置通知类型为 `NOTIFY_IN_APP_SEND`、`NOTIFY_SMS_SEND`、`NOTIFY_EMAIL_SEND`，IM 类型为 `IM_GROUP_CREATE`、`IM_GROUP_INVITE`、`IM_GROUP_MESSAGE_SEND`。
- **是否支持并行？** 当前只支持串行步骤。业务本地动作可直接实现 Handler，无需先建通用能力模块。
- **是否支持补偿？** 已有 Action 级开关、成功步骤逆序补偿与日志；具体入口、失败状态和边界见治理文档。
- **如何确认执行完成？** 检查 Action 是否为 `SUCCESS`，不能只看 Outbox 的 `DONE`。数据库回滚不会撤销下游副作用，Handler 仍需幂等。
- **如何验证示例？** 在仓库根目录按环境条件运行 `bash scripts/run-demo-smoke.sh`；恢复和并发相关变化可再运行 `bash scripts/run-demo-stability.sh`。执行前检查脚本的数据写入范围，区分真实联调与单元测试结果。

## 继续阅读

- [Action 定义与步骤扩展](step-type-extension-guide.md)
- [治理操作](ops-governance.md)
