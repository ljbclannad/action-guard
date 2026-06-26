# Architecture

## Goal

`action-guard` provides a reliable way to orchestrate asynchronous business side effects after a local transaction commits.

Its architecture centers on one guarantee:

`publish(action)` must never produce a state where the business transaction succeeds but the asynchronous side effect is silently lost.

## Architecture Principles

- Use the outbox pattern as the only reliable publish path.
- Separate reliable publish from message execution semantics.
- Keep the first version limited to serial steps.
- Treat governance as part of the runtime, not an external afterthought.
- Prefer explicit state transitions over implicit in-memory control flow.
- Separate business transaction success from downstream side effect success.

## Layered Architecture

### 1. Capability Layer

This layer owns business-facing execution capabilities.

Responsibilities:

- define capability-domain `stepType` handlers
- encapsulate downstream platform integration details
- expose uniform execution SPI implementations

Examples:

- IM collaboration capability
- notification capability
- future storage or approval capability

This layer must not own outbox consistency or MQ consumer semantics.

### 2. Publish / Outbox Layer

This layer owns reliable action publication and durable execution scheduling.

Responsibilities:

- accept `publish(action)` requests
- persist `action_instance` and `action_outbox` in one transaction
- resolve action definitions
- determine what execution work should be emitted next
- persist action and step state transitions

This layer is the source of truth for orchestration state.

### 3. Message Execution Layer

This layer owns asynchronous delivery and consumption semantics.

Responsibilities:

- publish outbox work to MQ
- consume MQ messages
- tolerate repeated consumption
- deduplicate or fence repeated execution attempts
- invoke runtime step execution callbacks
- coordinate MQ ack, retry, and dead-letter behavior

This layer is where at-least-once delivery becomes governable step execution.

## Logical Components

### 1. Publish API

Business applications call `ActionPublisher.publish(ActionRequest)`.

Responsibilities:

- validate request shape
- resolve the action definition name
- persist initial action instance state
- persist outbox work in the same local transaction

This API must not directly execute remote side effects.

### 2. Definition Registry

The definition registry loads action definitions from YAML or other configured sources.

Responsibilities:

- resolve action definition by name and version
- validate definition structure at startup or refresh time
- expose step-level execution metadata to the runtime

### 3. Persistence Layer

Persistence stores runtime state explicitly.

Core persisted entities:

- `action_instance`
- `action_step_instance`
- `action_outbox`
- `action_audit_log`

The persistence layer is responsible for durable state, not orchestration decisions.

### 4. Dispatcher

The dispatcher polls executable outbox rows and prepares them for asynchronous delivery.

Responsibilities:

- scan pending work using index-friendly conditions
- claim rows safely in clustered deployment
- hand claimed work to the message producer
- release or advance work after durable publish state is written

The dispatcher is the boundary between durable pending work and MQ delivery.

### 4.1 Message Producer

The message producer publishes claimed outbox work to the configured MQ transport.

Responsibilities:

- convert outbox work into transport messages
- attach message identity and idempotency metadata
- publish to topic or queue
- write durable publish result or failure state

### 4.2 Message Consumer

The message consumer receives execution messages from MQ and triggers step execution.

Responsibilities:

- deserialize action execution message
- perform repeated-consumption check
- fence duplicate execution when required
- invoke runtime execution callback
- apply ack, retry, or dead-letter decision

This is the layer the user referred to as the message consumption layer.

### 5. Runtime Executor

The runtime executor drives a single action instance through its serial steps.

Responsibilities:

- load current action and step state
- locate the next executable step
- invoke the correct step handler
- apply retry policy
- write result, next schedule, and state transitions
- trigger compensation or governance hooks when required

### 6. Step Handlers

Step handlers execute concrete side effects such as:

- HTTP call
- MQ message publish
- Spring bean method invocation
- webhook callback

Each handler must support:

- idempotent execution contract
- timeout contract
- structured result classification

### 6.1 Step Handler SPI

The runtime must not hardcode business step logic.

Instead, it should resolve each step through a uniform SPI such as:

```java
public interface ActionStepHandler {

    String stepType();

    StepExecutionResult execute(ActionStepContext context);
}
```

The SPI contract should make three things explicit:

- which `stepType` the handler owns
- what execution context the runtime provides
- how the handler reports success, retryable failure, and terminal failure

### 6.2 Handler Registration Model

Handlers should be contributed by modules and registered at application startup.

Recommended model in Spring Boot:

1. each adapter or business module exposes one or more `ActionStepHandler` beans
2. the starter collects all beans into a `StepHandlerRegistry`
3. the runtime resolves `stepType -> handler` through the registry

This allows one action definition to span multiple modules as long as every referenced `stepType` has exactly one registered handler.

Examples:

- `MQ_MESSAGE` from `action-guard-adapter-rabbitmq`
- `HTTP_CALL` from a core HTTP adapter
- `GROUP_INVITE` from an IM integration module
- `BEAN_INVOKE` from a local application module
- `NOTIFY_SMS_SEND` from a notification adapter module

### 6.3 Registry Responsibilities

The registry should:

- index handlers by `stepType`
- reject duplicate registrations for the same `stepType`
- expose lookup for forward execution
- expose lookup for compensation execution if compensation uses the same or a separate SPI
- support startup validation against loaded action definitions

### 6.4 Runtime Lookup Rule

When the runtime reaches a step:

1. it reads `stepType` from the resolved action definition
2. it queries the registry for the matching handler
3. it builds `ActionStepContext`
4. it invokes the handler
5. it persists the returned execution result

If no handler is registered for the `stepType`, the framework must not silently skip the step. It should fail definition validation at startup when possible, or fail the action deterministically with a clear governance-visible error.

### 6.5 Context Passed To Handlers

`ActionStepContext` should provide at least:

- action instance identity
- definition name and version
- business key
- resolved attributes
- current step definition
- current attempt number
- idempotency key
- deadline or timeout metadata
- access to previous persisted step outputs if that feature is enabled

Handlers should not need direct access to orchestration internals such as outbox claiming details.
Handlers should also not own MQ ack or duplicate-consumption handling.

### 6.6 Execution Result Contract

`StepExecutionResult` should be explicit enough for governance and retry:

- `SUCCESS`
- `RETRYABLE_FAILURE`
- `TERMINAL_FAILURE`

The result should also allow:

- normalized error code
- human-readable message
- optional structured output payload
- optional downstream receipt or message id

This keeps policy decisions in the runtime while letting handlers report accurate execution facts.

### 6.7 Compensation Handler Model

There are two acceptable first-version models:

- reuse `ActionStepHandler` and mark compensation as another step definition
- define a separate `ActionCompensationHandler` SPI

The preferred design is to keep compensation independently modeled when the downstream inverse action differs materially from the forward action.

In either case, the compensation registration contract must be explicit and startup-validatable.

### 6.8 Multi-Module Composition

A single action may span handlers from multiple modules.

Example:

- step 1 `IM_GROUP_CREATE` from an IM adapter
- step 2 `IM_GROUP_INVITE` from the same IM adapter
- step 3 `IM_GROUP_MESSAGE_SEND` from the same IM adapter
- step 4 `NOTIFY_SMS_SEND` from a notification adapter

The orchestration layer does not care which module owns the handler, only that:

- the `stepType` is registered once
- the handler contract is satisfied
- execution results are durably recorded

This is how the framework supports business flows such as "send message, then create group, then pull users into group" without coupling the runtime to one domain module.

### 6.9 Recommended Capability Modules

The project structure should express capability domains explicitly.

Recommended first-version adapter modules:

- `action-guard-adapter-rabbitmq`: MQ publish handlers
- `action-guard-adapter-kafka`: Kafka publish handlers
- `action-guard-adapter-im`: IM collaboration handlers
- `action-guard-adapter-notify`: notification handlers

Recommended first-version step types by module:

- RabbitMQ: `MQ_MESSAGE`
- Kafka: `KAFKA_MESSAGE`
- IM: `IM_GROUP_CREATE`, `IM_GROUP_INVITE`, `IM_GROUP_MESSAGE_SEND`
- Notify: `NOTIFY_IN_APP_SEND`, `NOTIFY_SMS_SEND`, `NOTIFY_EMAIL_SEND`

This keeps module boundaries aligned to integration domains rather than forcing one module per individual step type.

### 7. Governance Services

Governance services provide operator-facing control and observability.

Responsibilities:

- list and inspect action instances
- inspect step history and failure reasons
- perform manual retry, skip, cancel, and compensation actions
- publish alerts
- write audit logs for every operator action

## End-to-End Flow

### 1. Publish Phase

Inside the caller's local transaction:

1. domain data is updated
2. `action_instance` is inserted with initial state such as `PENDING`
3. `action_outbox` is inserted with dispatchable work
4. the transaction commits

If the transaction rolls back, neither the action instance nor the outbox row may remain visible.

### 2. Outbox Dispatch Phase

After commit:

1. dispatcher polls rows in `action_outbox`
2. dispatcher claims eligible rows using lease or status transition
3. claimed rows are handed to the message producer
4. message producer publishes execution messages to MQ

Cluster safety comes from durable claim semantics, not in-memory locks alone.

### 3. MQ Consumption Phase

After MQ delivery:

1. consumer receives the execution message
2. consumer checks message identity and repeated-consumption guard
3. non-executable duplicate deliveries are acknowledged without re-running the step
4. executable deliveries invoke the runtime executor

At-least-once delivery is expected. Duplicate-consumption safety is mandatory.

### 4. Execution Phase

For each claimed action:

1. runtime loads the definition and instance state
2. runtime finds the current serial step
3. runtime invokes the step handler
4. runtime persists step result
5. runtime either advances to the next step or schedules retry

If another step remains, the publish / outbox layer creates the next executable work and the message execution layer delivers it asynchronously again.

### 5. Completion Phase

The action reaches one of the terminal states:

- `SUCCESS`
- `FAILED`
- `CANCELLED`
- `COMPENSATED`
- `IGNORED`

`FAILED` means automatic progress stopped and operator action or compensation policy is now required.

## State Model

Suggested action states:

- `PENDING`: created but not yet claimed
- `DISPATCHING`: claimed and being processed
- `WAITING_RETRY`: waiting for next execution window
- `WAITING_MANUAL`: automatic execution stopped, operator action required
- `COMPENSATING`: compensation is executing
- `COMPENSATED`: compensation completed
- `SUCCESS`: all steps completed
- `FAILED`: unrecoverable failure
- `CANCELLED`: manually terminated
- `IGNORED`: published but intentionally not executed

Suggested step states:

- `PENDING`
- `RUNNING`
- `SUCCESS`
- `WAITING_RETRY`
- `FAILED`
- `SKIPPED`
- `COMPENSATING`
- `COMPENSATED`

## Retry Model

Retry is applied at the step level, not the whole action as a blind rerun.

Each step definition should declare:

- max attempts
- backoff policy
- timeout
- retryable error classification

A retry must write durable attempt count and next execution time before the worker releases control.

## MQ Consumption Semantics

The framework should assume at-least-once delivery from MQ.

That means the same execution message may be delivered more than once because of:

- consumer crash after step execution but before ack
- broker redelivery after timeout
- network ambiguity around ack result
- explicit replay or dead-letter recovery

Required protections:

- stable execution message id
- durable consume record or equivalent fencing state
- step-level idempotency key
- terminal duplicate detection before handler invocation

The message layer should protect the runtime from accidental repeated side effects, but the handler contract must still support idempotent downstream execution.

## Message Layer Responsibilities

The message execution layer should explicitly own:

- transport message schema
- consumer group strategy
- consume deduplication
- delayed retry or redelivery policy
- dead-letter handling
- replay support for governance operations

These concerns should not leak into capability modules.

## Compensation Model

Compensation is a first-class framework capability.

It is not a database rollback substitute. It is a forward recovery mechanism for already executed side effects.

Compensation rules:

- only successfully completed steps are eligible for compensation
- compensation runs in reverse step order
- each compensation attempt is durably recorded
- compensation failure can itself enter retry or manual governance flow

## Alerting Model

Alerts should be emitted on governance-significant events such as:

- final retry exhausted
- action enters `WAITING_MANUAL`
- compensation fails
- dispatcher claim stalls beyond threshold
- step latency breaches threshold

Alerting must not be the only source of truth. Durable state and audit logs remain authoritative.

## Transaction Boundaries

There are two critical transaction boundaries:

### Boundary A: Business Commit

The business update, action instance insert, and outbox insert must commit atomically in one local transaction.

### Boundary B: Runtime State Advancement

Each step execution result must be committed durably before worker ownership is released. The framework must never rely on in-memory progress after a remote side effect has occurred.

## What The First Version Explicitly Supports

- explicit action publish
- YAML-based action definition registry
- serial step execution
- step handler SPI and registry-driven module extension
- outbox-based dispatch
- step retry and timeout
- built-in compensation model
- alerting and operator governance

## What The First Version Explicitly Defers

- parallel branches
- conditional branches in the DSL
- saga choreography across multiple services
- cross-region active-active coordination
- visual workflow authoring
