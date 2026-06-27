# FAQ

## 1. 现在默认必须依赖 MySQL 吗

不是。

当前仓库默认演示路径已经切到 H2 file 模式：

- `examples/message-guard-demo` 默认使用 H2
- `action-guard-store-mysql` 默认示例配置也使用 H2

如果你要接近生产部署，再把 datasource 切换到 MySQL 即可。

## 2. 为什么模块名还是 `action-guard-store-mysql`

这是当前模块命名历史保留。

它本质上已经是 JDBC + MyBatis 的持久化实现模块，当前默认演示配置走 H2，并通过 H2 的 MySQL 兼容模式复用同一套 schema 和 mapper。

后续如果要进一步抽象模块命名，会优先考虑兼容迁移成本。

## 3. 最小跑通链路需要哪些基础设施

当前最小真实主链路需要：

- H2 文件库
- RabbitMQ

最小模块组合建议：

- `action-guard-spring-boot-starter`
- `action-guard-store-mysql`
- `action-guard-adapter-rabbitmq`
- 一个能力模块，例如 `action-guard-adapter-notify`

## 4. Kafka 现在是主路径吗

不是。

当前仓库真实主路径是 RabbitMQ。

第一版已经明确不做 Kafka 主路径建设：

- Kafka 模块仅占位保留
- 不作为默认接入组合
- 不作为示例、冒烟、稳定性验证的目标模块

如果你当前要接入生产或 PoC，请统一按 RabbitMQ 组合评估。

## 5. `stepType` 和 `target` 分别代表什么

当前规则是：

- `stepType` 代表能力类型
- `target` 代表具体 provider 或路由目标

例如：

```yaml
steps:
  - name: send-user-sms
    stepType: NOTIFY_SMS_SEND
    target: mock-sms
```

## 6. 什么时候应该自己写 `ActionStepHandler`

如果步骤只服务于当前业务系统，且暂时不需要沉淀成复用能力模块，直接写 `ActionStepHandler` 是最简单的方式。

如果它已经是明确的通用能力，例如短信、邮件、IM、Webhook，建议抽成独立 adapter 模块。

## 7. 这个项目当前支持多步并行吗

不支持。

当前第一版明确只支持串行步骤推进，这是有意收敛的设计边界。

## 8. 是否支持补偿

支持第一版真实补偿主路径，包含：

- Action 级补偿开关
- 成功步骤逆序补偿
- 补偿失败进入 `DEAD`
- 补偿日志持久化

但补偿重试等更完整的可靠性能力仍在后续增强范围内。

## 9. 如何验证 demo 是否真的跑通了真实链路

可以直接运行：

```bash
bash scripts/run-demo-smoke.sh
```

或：

```bash
bash scripts/run-demo-stability.sh
```

前者做单次冒烟，后者做多轮稳定性验证。

## 10. 接入时优先看哪些文档

建议顺序：

1. [Quick Start](/Users/lejinbo/LLM/message-guard/docs/quick-start.md)
2. [Starter Config](/Users/lejinbo/LLM/message-guard/docs/starter-config.md)
3. [Module Selection](/Users/lejinbo/LLM/message-guard/docs/module-selection.md)
4. [Module Architecture](/Users/lejinbo/LLM/message-guard/docs/module-architecture.md)
5. [Definition Spec](/Users/lejinbo/LLM/message-guard/docs/definition-spec.md)
