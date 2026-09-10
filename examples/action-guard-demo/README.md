# action-guard-demo

默认示例展示单步成功链路；[故障与治理演示](#故障与治理演示)通过独立的 `fault-demo` profile 展示自动重试和人工跳过。

本应用在 `application.yml` 中显式配置 `action.guard.store.type=mysql` 和 `action.guard.execution.transport=rabbitmq`，默认连接当前服务器的 MySQL 和 RabbitMQ；`fault-demo` 沿用该配置。MySQL
驱动由存储模块以运行时依赖提供。原 H2 数据不会自动迁移；需要 H2 时显式启用 `h2` profile，执行传输仍沿用 `rabbitmq`。

不要通过删除传输选择来切换本地执行：未配置时不装配框架默认 RabbitMQ 生产者和消费者，没有自定义生产者时 Outbox 不发送，也不会自动执行步骤；Spring Boot 的 `RabbitTemplate`
仍可供其他业务使用。示例的业务配置按同一选择条件启用拓扑，并非适配器自动提供拓扑；用户自定义拓扑需自行添加启用条件。选择 `rabbitmq`
后缺少适配器或模板会启动报错，但不进行网络探活。取值约束和自定义生产者说明见 [执行传输选择](../../docs/guides/starter-config.md#执行传输选择)。

## 运行前置条件

这个 demo 默认依赖以下远程服务：

- MySQL 8.4，公网 TCP 3306 端口
- RabbitMQ 4，公网 AMQP 5672 端口

连接信息不写入仓库。默认 profile 从被忽略的 `.local/action-guard.env` 或环境变量读取 `MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`、`DEMO_RABBITMQ_HOST`、`DEMO_RABBITMQ_PORT`、`DEMO_RABBITMQ_USERNAME`、`DEMO_RABBITMQ_PASSWORD` 和 `DEMO_RABBITMQ_VIRTUAL_HOST`。

服务器已执行 `action-guard-mysql-schema.sql`，默认关闭应用侧重复初始化；新数据库需先执行该脚本。应用会自动声明以下 RabbitMQ 拓扑：

- exchange: `action.guard.execute`
- queue: `action.guard.execute.queue`
- routing key prefix: `action.execute`

## 本地运行

在仓库根目录运行。Spring 自动导入被忽略的 `.local/action-guard.env`；该文件应保存远程基础设施地址、账号和密码，不会打包进 JAR。其他机器通过环境变量提供同名配置，或用 `ACTION_GUARD_LOCAL_CONFIG` 指定本地配置文件的绝对路径。IDE 的工作目录应设置为仓库根目录；Maven 启动已配置该工作目录。

MySQL URL 使用 `sslMode=REQUIRED` 加密传输但不校验服务端证书身份；RabbitMQ 当前为未加密 AMQP，可用服务器安全组限制来源 IP。管理页面端口不是 AMQP 连接端口。

先构建依赖模块：

```bash
mvn -pl examples/action-guard-demo -am install -DskipTests
```

再启动 demo：

```bash
mvn -f examples/action-guard-demo/pom.xml spring-boot:run
```

## 最小冒烟验证

如果你只想快速确认默认演示链路能跑通，可以直接运行：

```bash
bash scripts/run-demo-smoke.sh
```

预期脚本会在日志里看到：

```text
status=SUCCESS
```

## 最小稳定性验证

可以直接使用仓库自带脚本，对当前 demo 做一组最小并发稳定性验证：

```bash
ACTION_GUARD_STABILITY_RUNS=10 \
ACTION_GUARD_STABILITY_PARALLELISM=3 \
bash scripts/run-demo-stability.sh
```

默认行为：

- 先执行一次 `compile`
- 然后并发启动多次 demo 实例
- 每个实例都会分配独立 `SERVER_PORT`
- 每个实例显式启用 `h2` profile，并分配独立 `DEMO_H2_PATH`；RabbitMQ 默认连接本机 `localhost:5672`
- 每个实例都会真实走一条 `publish -> RabbitMQ -> runtime -> SUCCESS` 链路
- 最后在 `.tmp/action-guard-stability/<timestamp>/` 下输出分 run 日志，并汇总成功/失败数

这样可以避免并发验证时出现固定 `8080` 端口冲突，或多个实例共用同一个 H2 文件库导致的锁冲突。

可用环境变量：

```bash
ACTION_GUARD_STABILITY_RUNS
ACTION_GUARD_STABILITY_PARALLELISM
ACTION_GUARD_STABILITY_BASE_PORT
ACTION_GUARD_STABILITY_BUILD_FIRST
ACTION_GUARD_STABILITY_LOG_DIR
```

## 可覆盖环境变量

远程 demo 所需的连接信息通过以下环境变量或 `.local/action-guard.env` 提供：

```bash
MYSQL_URL
MYSQL_USERNAME
MYSQL_PASSWORD
RABBITMQ_PASSWORD
ACTION_GUARD_LOCAL_CONFIG
DEMO_DB_POOL_MIN_IDLE
DEMO_DB_POOL_MAX_SIZE
DEMO_DB_POOL_IDLE_TIMEOUT_MS
DEMO_DB_POOL_MAX_LIFETIME_MS
DEMO_DB_POOL_CONNECTION_TIMEOUT_MS
DEMO_DB_POOL_VALIDATION_TIMEOUT_MS
DEMO_RABBITMQ_HOST
DEMO_RABBITMQ_PORT
DEMO_RABBITMQ_USERNAME
DEMO_RABBITMQ_PASSWORD
DEMO_RABBITMQ_VIRTUAL_HOST
```

`DEMO_H2_PATH`、`DEMO_H2_USERNAME` 和 `DEMO_H2_PASSWORD` 仅在 `h2` profile 下使用。

## 预期输出

首次成功运行时，控制台应出现类似输出：

```text
send sms to 13800000000
actionName=demo-notify-success
bizKey=order:demo-<timestamp>
status=SUCCESS
```

## 这条链路覆盖

这个 demo 跑的是当前项目已经落地的真实主路径，而不是额外的手动触发逻辑：

- 本地 YAML Action 定义加载
- 发布后写入 `action_instance` 和 `action_outbox`
- 发布主路径自动将 Outbox 投递到真实 RabbitMQ exchange / queue
- RabbitMQ consumer 从真实队列消费并回调 runtime 执行 step
- 单步 Action 进入 `SUCCESS`

## 排查提示

- 如果启动卡在数据库连接，优先检查服务器 3306 是否可达、密码是否加载、数据库名是否为 `action-guard`
- 如果没有看到 `send sms to ...`，优先检查 RabbitMQ 是否可连、exchange / queue 是否成功声明
- 如果提示表不存在，确认连接到正确数据库并已执行初始化脚本；`h2` profile 下另需检查文件目录权限和占用情况

## 故障与治理演示

### 启动

先按前文构建依赖、准备远程连接密码，再启动：

```bash
mvn -f examples/action-guard-demo/pom.xml spring-boot:run \
  -Dspring-boot.run.profiles=fault-demo
```

该 profile 关闭启动时自动发布，加载 `fault-actions/*.yml`，并启用现有 ops-api 的查询、命令和审计接口。默认成功示例的 `/api/publish` 在此 profile 下关闭；`GET /api/actions/{id}` 改为返回治理详情，其中 `status` 仍在顶层。

治理 API 当前没有鉴权，因此此 profile 默认只监听 `127.0.0.1`，仅用于本地演示。默认数据库位置和 RabbitMQ 拓扑沿用前文配置；不要同时运行消费同一队列但加载不同定义的默认 demo 和故障 demo。

两个场景只模拟 Handler 结果，不调用真实业务下游。`fail-once` 根据持久化步骤的 `attemptCount` 判断首次失败，进程重启不会重置计数；这是故障注入实现，不是业务 Handler 幂等性的实现范例。

### 场景一：第二步自动重试成功

```bash
curl -sS -X POST http://localhost:8080/api/demo/scenarios/auto-retry
```

响应返回 `actionInstanceId`；每次发布自动生成新的业务键。将返回的 ID 填入下面的变量：

```bash
ACTION_ID='返回的 actionInstanceId'
curl -sS "http://localhost:8080/api/actions/$ACTION_ID"
curl -sS "http://localhost:8080/api/actions/$ACTION_ID/steps"
curl -sS "http://localhost:8080/api/actions/$ACTION_ID/timeline"
```

预期过程：`prepare` 成功，`call-downstream` 首次返回 `DEMO_TRANSIENT_FAILURE`，Action 进入 `RETRYING`。重试等待 5 秒，再由每 5 秒运行的恢复扫描派发，最终进入 `SUCCESS`。具体耗时还受扫描时机和消息处理影响。

验收：两个步骤均为 `SUCCESS`，`prepare.attemptCount=1`，`call-downstream.attemptCount=2`；前序成功步骤没有因第二步重试而重新执行。

### 场景二：等待重试时人工跳过

```bash
curl -sS -X POST http://localhost:8080/api/demo/scenarios/manual-skip
```

用本次返回的 ID 更新 `ACTION_ID`，查询详情与步骤。等待 Action 进入 `RETRYING`，第二步错误码为 `DEMO_DOWNSTREAM_UNAVAILABLE` 后，再执行：

```bash
curl -sS -X POST "http://localhost:8080/api/actions/$ACTION_ID/skip" \
  -H 'X-Action-Guard-Operator: demo-operator'
curl -sS "http://localhost:8080/api/actions/$ACTION_ID"
curl -sS "http://localhost:8080/api/actions/$ACTION_ID/steps"
curl -sS "http://localhost:8080/api/audit-logs?actionInstanceId=$ACTION_ID"
```

第二步持续失败，配置最多 10 次重试、每次间隔 60 秒，为人工操作留出时间。必须在 `RETRYING` 时跳过；若已耗尽重试进入 `FAILED`，当前治理规则不允许跳过，请重新发布场景。

验收：Action 变为 `SUCCESS`，第一步尝试次数仍为 1；被跳过步骤标记为 `SUCCESS`，尝试次数不会因跳过增加，审计中包含 `SKIP` 和 `demo-operator`。这里的成功表示人工接受省略可选副作用，不能理解为下游调用真实成功。

### 自动化验证与边界

在仓库根目录执行：

```bash
mvn -pl examples/action-guard-demo -am \
  -Dtest=DemoFaultScenarioTest -Dsurefire.failIfNoSpecifiedTests=false test
```

测试使用 H2 内存库、真实执行与治理服务、MockMvc 和替换的消息发送端，显式驱动执行回调，验证重试到期约束、前序步骤不重跑、人工跳过与审计落库。测试不连接 RabbitMQ，不代表真实 MySQL / RabbitMQ 或进程中断恢复验证。

本批覆盖自动重试和等待重试期间的人工跳过。重试耗尽后的人工恢复、补偿、MQ 中断和进程停机恢复尚未纳入此演示。
