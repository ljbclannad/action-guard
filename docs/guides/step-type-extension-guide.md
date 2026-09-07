# Action 定义与步骤扩展

文档入口：[文档导航](../README.md)。

## 目标

这份文档面向要扩展 `action-guard` 能力的人，回答三个问题：

1. 什么时候应该新增一个 `stepType`
2. 新增后要提供哪些代码和配置
3. 怎么保证它能被 runtime 正确加载、执行和治理

## 当前定义字段

当前 YAML 加载器接受以下字段，配置时使用扁平的重试与超时字段：

| 层级 | 字段 | 含义 |
| --- | --- | --- |
| Action | `name`、`steps` | 定义名称、有序步骤列表 |
| Action | `version` | 定义版本，缺省为 `1`；目前不等于已实现运行期多版本隔离 |
| Action | `description`、`compensationEnabled` | 描述、补偿开关；补偿缺省关闭 |
| Step | `name`、`stepType`、`target` | 步骤名、能力类型、provider 或路由目标 |
| Step | `maxRetryCount` | 与 `ActionRetryPolicy` 共同决定可重试次数 |
| Step | `retryBackoffMillis` | 重试等待毫秒数，未到期的 Outbox 由恢复扫描处理 |
| Step | `timeoutMillis` | Handler 返回后检查执行耗时，不主动中断阻塞调用 |

```yaml
name: order-label-flow
version: 1
description: 同步订单标签
compensationEnabled: false
steps:
  - name: sync-order-label
    stepType: ORDER_LABEL_SYNC
    target: order-service
    maxRetryCount: 3
    retryBackoffMillis: 5000
    timeoutMillis: 10000
```

请求参数通过 `ActionRequest.attributes` 和 `ActionStepRequest.payload` 传入；Handler 从 `ActionStepContext` 读取它们。当前 YAML 加载器不会自动执行请求模板或表达式。

以 [YAML 加载器](../../publish-outbox-layer/action-guard-core/src/main/java/io/github/actionguard/core/runtime/definition/YamlActionDefinitionLoader.java) 和 [定义校验器](../../publish-outbox-layer/action-guard-core/src/main/java/io/github/actionguard/core/runtime/definition/ActionDefinitionValidator.java) 为实际支持范围：校验定义与步骤名称、非空步骤、重复步骤名和重试/超时数值等。Handler 类型还需与运行时注册表匹配。

旧设计中的嵌套 `defaults.retry`、`timeout: 10s`、请求模板、步骤级补偿 DSL、自定义幂等表达式及多版本定义中心仍属于规划，不能照此配置并假定生效；DAG、并行分支和复杂条件 DSL 也不在当前范围内。

## 执行契约

- `stepType` 在 Handler 注册表中唯一；`target` 由能力 Handler 解释与路由，不直接等于 Spring Bean 名。
- Handler 返回 `StepExecutionResult.succeeded()` 或 `failed(errorCode, errorMessage)`，重试与终态选择由 `ActionRetryPolicy` 决定，返回值本身没有独立的可重试标记。
- 结果落库失败或消息恢复可能导致再次执行，幂等键应来自稳定业务标识，并在可行时透传下游。Action 发布幂等不能替代 Step 的副作用幂等。
- 补偿通过独立的 `ActionCompensator` 扩展，业务负责界定哪些副作用可撤销；启用补偿不等于获得分布式回滚能力。
- 变更步骤顺序或类型前处理仍在运行的旧 Action；定义版本兼容性见维护文档。

## 什么时候应该新增一个 `stepType`

适合新增 `stepType` 的场景：

- 你要封装一类可复用副作用能力，例如短信、邮件、IM、HTTP 回调
- 这类能力需要统一的输入模型和执行结果模型
- 你希望业务 YAML 只关心能力类型和 provider，不关心具体 Bean 名称

不建议新增 `stepType` 的场景：

- 只是一个单项目内部的小逻辑
- 还没有稳定的输入输出边界
- 更适合直接在业务应用里写一个 `ActionStepHandler`

## 当前设计规则

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

## 推荐扩展路径

推荐顺序：

1. 先判断它是“通用能力模块”还是“单业务步骤”
2. 如果是通用能力模块，新增 adapter 模块和 sender/provider SPI
3. 如果只是业务自定义步骤，直接在业务应用里实现 `ActionStepHandler`

## 路径 A：新增一个通用能力模块

以新的通知/协作类能力为例，推荐包含这些部分。

### 1. 定义新的 `stepType`

命名建议：

- 使用大写下划线风格
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

建议像现有 notify / im 模块一样，provider 至少暴露：

- `provider()`
- 一个执行方法，例如 `send(...)`、`create(...)`、`invite(...)`

这样 starter 才能在启动时把多个 provider 收集进 registry。

### 5. 注册到 Spring

模块内通常需要提供：

- auto-configuration
- handler bean
- provider registry bean

业务应用侧只需要实现 provider Bean，即可被自动发现。

## 路径 B：新增一个业务本地 Step

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

## 步骤命名与参数

新增 `stepType` 后，建议同步约束 YAML 写法：

- `name` 表达业务步骤名
- `stepType` 表达能力类型
- `target` 表达 provider 或业务路由目标
- 执行参数通过发布请求传入，YAML 只声明加载器支持的定义字段

例如：

```yaml
steps:
  - name: apply-order-tag
    stepType: CRM_TAG_APPLY
    target: salesforce-main
```

## 重试 / 超时 / 补偿设计考虑

如果一个新 `stepType` 会进入生产使用，至少要明确：

- 是否允许 retry
- retry 后是否可能产生重复副作用
- 幂等键是什么
- timeout 后应该算 retryable 还是 terminal
- 是否需要 compensator

推荐做法：

- provider 侧以业务幂等键兜底
- handler 侧返回清晰的成功或失败结果及错误码，重试策略负责分类
- 补偿逻辑单独建 compensator，不把正向和逆向逻辑混在一起

## 治理可见性要求

一个可上线的 `stepType`，至少应该保证治理侧能看到：

- step name
- step type
- target
- attempt count
- last error

如果你新增的是通用能力模块，建议再补：

- provider-level error code mapping
- 对关键失败场景的 alert event details

## 测试建议

最少建议覆盖：

1. handler 能正确匹配 `stepType`
2. provider 能正确匹配 `target`
3. 成功路径能推进到下一步
4. retryable failure 会进入重试
5. terminal failure 会进入失败或补偿
6. 重复执行不会产生不可接受的双重副作用

## 自查清单

新增一个 `stepType` 前，建议自查：

- [ ] 名称是否稳定且与 provider 解耦
- [ ] 输入输出模型是否清晰
- [ ] `target` 路由规则是否清晰
- [ ] 幂等策略是否明确
- [ ] retry / timeout 语义是否明确
- [ ] 是否需要 compensator
- [ ] 是否有最小测试覆盖
- [ ] 是否需要补文档示例 YAML
