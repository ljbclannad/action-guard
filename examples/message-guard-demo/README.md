# action-guard-demo

## 运行前置条件

这个 demo 依赖以下本地服务：

- MySQL 8.x，可写账号
- RabbitMQ 3.x，开启 AMQP 5672 端口

默认值来自 [application.yml](/Users/lejinbo/LLM/message-guard/examples/message-guard-demo/src/main/resources/application.yml:1)：

- MySQL: `localhost:3306/action_guard_demo`
- 用户名: `root`
- 密码: `root`
- RabbitMQ: `localhost:5672`
- RabbitMQ 用户名: `guest`
- RabbitMQ 密码: `guest`

应用启动时会自动执行 `classpath:db/action-guard-mysql-schema.sql` 初始化表结构，并自动声明以下 RabbitMQ 拓扑：

- exchange: `action.guard.execute`
- queue: `action.guard.execute.queue`
- routing key prefix: `action.execute`

## 本地运行

先构建依赖模块：

```bash
mvn -pl examples/message-guard-demo -am install -DskipTests
```

再启动 demo：

```bash
mvn -f examples/message-guard-demo/pom.xml spring-boot:run
```

## 可覆盖环境变量

如果你的本地环境不是这组默认值，可以覆盖这些环境变量：

```bash
DEMO_MYSQL_HOST
DEMO_MYSQL_PORT
DEMO_MYSQL_DATABASE
DEMO_MYSQL_USERNAME
DEMO_MYSQL_PASSWORD
DEMO_MYSQL_POOL_MIN_IDLE
DEMO_MYSQL_POOL_MAX_SIZE
DEMO_MYSQL_POOL_IDLE_TIMEOUT_MS
DEMO_MYSQL_POOL_MAX_LIFETIME_MS
DEMO_MYSQL_POOL_CONNECTION_TIMEOUT_MS
DEMO_MYSQL_POOL_VALIDATION_TIMEOUT_MS
DEMO_RABBITMQ_HOST
DEMO_RABBITMQ_PORT
DEMO_RABBITMQ_USERNAME
DEMO_RABBITMQ_PASSWORD
```

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

- 如果启动卡在数据库连接，优先检查 `DEMO_MYSQL_*` 环境变量和 MySQL 是否允许本机连接
- 如果没有看到 `send sms to ...`，优先检查 RabbitMQ 是否可连、exchange / queue 是否成功声明
- 如果表结构初始化失败，优先检查数据库账号是否具备建库建表权限
