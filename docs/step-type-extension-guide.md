# StepType Extension Guide

## Goal

这份文档面向要扩展 `action-guard` 能力的人，回答三个问题：

1. 什么时候应该新增一个 `stepType`
2. 新增后要提供哪些代码和配置
3. 怎么保证它能被 runtime 正确加载、执行和治理

## When To Add A New `stepType`

适合新增 `stepType` 的场景：

- 你要封装一类可复用副作用能力，例如短信、邮件、IM、HTTP 回调
- 这类能力需要统一的输入模型和执行结果模型
- 你希望业务 YAML 只关心能力类型和 provider，不关心具体 Bean 名称

不建议新增 `stepType` 的场景：

- 只是一个单项目内部的小逻辑
- 还没有稳定的输入输出边界
- 更适合直接在业务应用里写一个 `ActionStepHandler`

## Current Design Rule

当前仓库采用两层路由：

- `stepType` 表达能力类型
- `target` 表达具体 provider

例如：

```yaml
steps:
  - name: send-user-sms
    stepType: NOTIFY_SMS_SEND
    target: mock-sms
```

这里 runtime 先通过 `stepType=NOTIFY_SMS_SEND` 找到通知能力处理器，再通过 `target=mock-sms` 找到真正的 provider。

## Recommended Extension Path

推荐顺序：

1. 先判断它是“通用能力模块”还是“单业务步骤”
2. 如果是通用能力模块，新增 adapter 模块和 sender/provider SPI
3. 如果只是业务自定义步骤，直接在业务应用里实现 `ActionStepHandler`

## Path A: Add A Generic Capability Module

以新的通知/协作类能力为例，推荐包含这些部分。

### 1. 定义新的 `stepType`

命名建议：

- 大写下划线风格
- 动作语义清晰
- 尽量稳定，不把 provider 名写进 `stepType`

推荐：

- `PAYMENT_REFUND_SUBMIT`
- `CRM_TAG_APPLY`

不推荐：

- `ALIYUN_SMS_SEND`
- `WECOM_GROUP_INVITE`

### 2. 定义请求与结果模型

建议在能力模块内定义：

- request model
- provider SPI
- result model

这样 runtime 只依赖统一能力接口，不依赖具体平台。

### 3. 提供能力 Handler

Handler 负责：

- 解析 step attributes
- 校验 `target`
- 路由到正确 provider
- 把 provider 结果翻译成 runtime 可识别的执行结果

### 4. 提供 provider SPI

建议像现有 notify / im 模块一样，provider 必须暴露：

- `provider()`
- 一个执行方法，例如 `send(...)`、`create(...)`、`invite(...)`

这样 starter 能在启动时把多个 provider 收集进 registry。

### 5. 注册到 Spring

模块内需要提供：

- auto-configuration
- handler bean
- provider registry bean

业务应用侧只需要实现 provider Bean，即可被自动发现。

## Path B: Add A Business-Local Step

如果你的步骤只服务于当前业务系统，最小方式是直接实现 `ActionStepHandler`：

```java
@Component
class SyncOrderLabelStepHandler implements ActionStepHandler {

    @Override
    public String stepType() {
        return "ORDER_LABEL_SYNC";
    }

    @Override
    public StepExecutionResult execute(ActionStepContext context) {
        return StepExecutionResult.succeeded();
    }
}
```

适用场景：

- 只在一个服务里使用
- 暂时不打算抽象成共享模块
- 业务语义强于平台语义

## YAML Design Recommendations

新增 `stepType` 后，建议同步约束 YAML 写法：

- `name` 表达业务步骤名
- `stepType` 表达能力类型
- `target` 表达 provider 或业务路由目标
- `attributes` 只放执行所需参数

例如：

```yaml
steps:
  - name: apply-order-tag
    stepType: CRM_TAG_APPLY
    target: salesforce-main
    attributes:
      tagCode: cancelled
```

## Retry / Timeout / Compensation Considerations

如果一个新 `stepType` 会进入生产使用，至少要明确：

- 是否允许 retry
- retry 后是否可能产生重复副作用
- 幂等键是什么
- timeout 后应该算 retryable 还是 terminal
- 是否需要 compensator

推荐做法：

- provider 侧以业务幂等键兜底
- handler 侧只返回清晰的成功 / 可重试失败 / 不可重试失败
- 补偿逻辑单独建 compensator，不把正向和逆向逻辑混在一起

## Governance Visibility Requirements

一个可上线的 `stepType`，至少应该保证治理侧能看到：

- step name
- step type
- target
- attempt count
- last error

如果你新增的是通用能力模块，建议再补：

- provider-level error code mapping
- 对关键失败场景的 alert event details

## Testing Recommendations

最少建议覆盖：

1. handler 能正确匹配 `stepType`
2. provider 能正确匹配 `target`
3. 成功路径能推进到下一步
4. retryable failure 会进入重试
5. terminal failure 会进入失败或补偿
6. 重复执行不会产生不可接受的双重副作用

## Checklist

新增一个 `stepType` 前，建议自查：

- [ ] 名称是否稳定且与 provider 解耦
- [ ] 输入输出模型是否清晰
- [ ] `target` 路由规则是否清晰
- [ ] 幂等策略是否明确
- [ ] retry / timeout 语义是否明确
- [ ] 是否需要 compensator
- [ ] 是否有最小测试覆盖
- [ ] 是否需要补文档示例 YAML
