# 可观测性说明

## 目标

这份文档说明 `action-guard` 当前已经内建的告警与 metrics 语义，以及接入方如何扩展自己的监控实现。

当前版本的设计目标不是一次性提供完整监控平台，而是先把主链路的关键事件标准化，让使用方能够：

- 看到关键失败与卡住信号
- 统计主链路成功/失败/补偿结果
- 挂接自定义 `ActionMetricsRecorder` 对接自己的监控系统

## 告警能力

当前标准告警事件统一使用 `ActionAlertEvent` 建模，核心字段包括：

- `type`
- `level`
- `title`
- `message`
- `actionName`
- `actionInstanceId`
- `stepName`
- `stepType`
- `occurredAt`
- `details`

当前主路径已接入的告警类型包括：

- `RETRIES_EXHAUSTED`
- `COMPENSATION_FAILED`
- `CONSUME_FAILURE`
- `DEAD_LETTER`
- `OUTBOX_PUBLISH_FAILED`
- `ACTION_STUCK`

如果引入 `action-guard-alert-webhook` 并配置 `action.guard.alert.webhook.*`，这些事件会被直接投递到外部 webhook。

## 当前内建 metrics

当前默认指标模型以 counter 为主，由 `ActionMetricsRecorder.increment(...)` 统一承载。

starter 默认会注册内存版 recorder；如果你要接入 Micrometer、Prometheus 或公司内部平台，可以直接自定义 `ActionMetricsRecorder` Bean 覆盖默认实现。

### 1. 告警类计数

- `action.guard.alert.published`
- `action.guard.retry.exhausted`
- `action.guard.compensation.failed`
- `action.guard.consume.failed`
- `action.guard.dead.letter`
- `action.guard.outbox.publish.failed`
- `action.guard.action.stuck`

### 2. 运行结果计数

- `action.guard.step.succeeded`
- `action.guard.step.failed`
- `action.guard.step.timed_out`
- `action.guard.action.succeeded`
- `action.guard.action.failed`
- `action.guard.action.compensated`

### 3. 治理操作计数

- `action.guard.governance.command`

用于统计 `RETRY / SKIP / CANCEL / COMPENSATE` 等治理命令的成功与失败次数。

## 当前 tag 语义

当前指标 tag 以低复杂度、可直接聚合为目标，主要包括：

- `actionName`
- `stepType`
- `result`
- `errorCode`
- `command`

并不是每个指标都会带所有 tag。

例如：

- `action.guard.step.succeeded` 带 `actionName + stepType + result`
- `action.guard.action.failed` 带 `actionName + stepType + result + errorCode`
- `action.guard.governance.command` 带 `command + result`

## 接入自定义指标实现

最小方式是自己提供一个 `ActionMetricsRecorder` Bean：

```java
@Bean
ActionMetricsRecorder actionMetricsRecorder(MeterRegistry registry) {
    return (metricName, tags) -> Counter.builder(metricName)
            .tags(tags.entrySet().stream()
                    .flatMap(entry -> java.util.stream.Stream.of(entry.getKey(), entry.getValue()))
                    .toArray(String[]::new))
            .register(registry)
            .increment();
}
```

当前官方 SPI 只要求支持计数递增，不要求 duration / histogram / gauge。

如果你需要更丰富的指标模型，建议先在自定义 recorder 内部做映射，而不是直接改动框架对外 SPI。

## Micrometer / Prometheus 接入示例

如果你的应用已经使用 Spring Boot Actuator + Micrometer，可以直接用一个适配 Bean 把框架计数接进去：

```java
@Bean
ActionMetricsRecorder actionMetricsRecorder(MeterRegistry registry) {
    return (metricName, tags) -> Counter.builder(metricName)
            .tags(tags.entrySet().stream()
                    .flatMap(entry -> java.util.stream.Stream.of(entry.getKey(), entry.getValue()))
                    .toArray(String[]::new))
            .register(registry)
            .increment();
}
```

最小依赖示例：

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

最小配置示例：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    prometheus:
      enabled: true
```

这样接入后，`action.guard.*` 计数会进入你的 Micrometer registry，并通过 `/actuator/prometheus` 暴露给 Prometheus 抓取。

## 当前边界

当前版本还没有内建：

- 标准化 timer / histogram
- Prometheus 指标暴露端点
- 预制 Grafana dashboard
- 多告警通道聚合编排

因此更准确的定位是：

- 框架已经提供主链路事件与计数语义
- 监控平台接入与展示层由使用方按自身环境接管
