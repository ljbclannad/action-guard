# 快速开始

## 目标

这份文档面向第一次接入 `action-guard` 的 Spring Boot 3 应用，目标是让你用最少的步骤跑通一条真实链路：

- 发布 Action
- 写入 H2 或 MySQL 兼容存储状态
- 发布到 RabbitMQ
- 消费并执行 step
- 最终进入 `SUCCESS`

## 1. 添加依赖

最小可运行组合建议：

```xml
<dependencies>
  <dependency>
    <groupId>io.github.actionguard</groupId>
    <artifactId>action-guard-spring-boot-starter</artifactId>
  </dependency>
  <dependency>
    <groupId>io.github.actionguard</groupId>
    <artifactId>action-guard-adapter-rabbitmq</artifactId>
  </dependency>
  <dependency>
    <groupId>io.github.actionguard</groupId>
    <artifactId>action-guard-store-mysql</artifactId>
  </dependency>
</dependencies>
```

如果你的 action 需要短信、邮件、IM 等能力，再按需追加能力模块。

## 2. 准备基础设施

当前最小主链路需要：

- H2 文件库或 MySQL
- RabbitMQ 3.x

开源演示默认建议：

- 先使用 H2 文件库跑通主链路
- 等接入稳定后再切换到 MySQL

应用侧至少要准备：

- `spring.datasource.*`
- `spring.rabbitmq.*`

## 3. 添加应用配置

可以直接从模板复制：

- [action-guard-minimal-application.yml](/Users/lejinbo/LLM/action-guard/docs/templates/action-guard-minimal-application.yml)

starter 默认配置说明见：

- [Starter 配置说明](/Users/lejinbo/LLM/action-guard/docs/starter-config.md)

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

## 常见接入组合

最常见的三种组合：

- 本地最小闭环：`starter + rabbitmq + store-mysql + 一个能力模块`，默认用 H2 文件库
- 生产基础组合：`starter + rabbitmq + store-mysql + webhook alert + 业务能力模块`，再把 datasource 切到 MySQL
- 只接入编排内核：`starter + store-mysql`，由业务自己提供全部 step handlers

下一步建议：

- [模块选择建议](/Users/lejinbo/LLM/action-guard/docs/module-selection.md)
- [定义规范](/Users/lejinbo/LLM/action-guard/docs/definition-spec.md)
- [治理操作](/Users/lejinbo/LLM/action-guard/docs/ops-governance.md)
