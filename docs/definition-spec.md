# Definition Spec

## Purpose

This document defines the first-version action definition DSL for `action-guard`.

The DSL is intentionally constrained:

- one action definition describes one business side-effect flow
- steps execute only in serial order
- reliability and governance are more important than workflow expressiveness

## Definition Shape

An action definition contains:

- action metadata
- execution policy defaults
- an ordered list of step definitions
- optional alert and compensation behavior

Example:

```yaml
name: order-cancel-flow
description: Cancel order follow-up actions after order state update
version: 1
enabled: true
idempotency:
  scope: ACTION
  keyTemplate: "${bizKey}"
alerts:
  onWaitingManual: P1
  onCompensationFailure: P1
defaults:
  retry:
    maxAttempts: 5
    backoff:
      mode: EXPONENTIAL
      initialDelay: 5s
      maxDelay: 10m
  timeout: 10s
steps:
  - name: revoke-coupon
    stepType: HTTP_CALL
    target: coupon-service/revoke
    request:
      bodyTemplate:
        orderId: "${attributes.orderId}"
        userId: "${attributes.userId}"
    compensation:
      stepType: HTTP_CALL
      target: coupon-service/restore
      request:
        bodyTemplate:
          orderId: "${attributes.orderId}"
          userId: "${attributes.userId}"
  - name: notify-user
    stepType: MQ_MESSAGE
    target: topic.user.order-cancelled
    retry:
      maxAttempts: 10
      backoff:
        mode: FIXED
        delay: 30s
```

## Action Fields

### Required

- `name`: globally unique action definition name
- `steps`: ordered non-empty step list

### Optional

- `description`: human-readable description
- `version`: definition version for rollout control
- `enabled`: whether new publishes may use this definition
- `defaults`: default retry and timeout policy
- `idempotency`: action-level idempotency policy
- `alerts`: action-level alert policy

## Action Idempotency

Action-level idempotency prevents duplicate publish requests from creating duplicate executable actions.

Fields:

- `scope`: `ACTION` or `DEFINITION_VERSION`
- `keyTemplate`: expression resolved from request context

Recommended first-version behavior:

- duplicate publish with same resolved key returns the existing action instance identity
- duplicate publish must not create another active outbox row

## Step Definition

Each step is executed exactly in list order.

Required fields:

- `name`: unique within the action
- `stepType`: handler type
- `target`: handler-specific destination or identifier

Optional fields:

- `description`
- `retry`
- `timeout`
- `idempotency`
- `request`
- `successResult`
- `failurePolicy`
- `compensation`
- `alerts`

## Step Types

The first version should standardize a small built-in set:

- `HTTP_CALL`
- `MQ_MESSAGE`
- `BEAN_INVOKE`
- `WEBHOOK`
- `IM_GROUP_CREATE`
- `IM_GROUP_INVITE`
- `IM_GROUP_MESSAGE_SEND`
- `NOTIFY_IN_APP_SEND`
- `NOTIFY_SMS_SEND`
- `NOTIFY_EMAIL_SEND`

Adapters may contribute additional types, but the runtime contract remains the same.

## Step Type Registration

`stepType` is not just a label in YAML. It is the lookup key used by the runtime to find the actual step executor.

Registration rules:

- each `stepType` must map to exactly one active handler
- handlers may be provided by framework modules or business modules
- duplicate registrations for the same `stepType` must fail startup
- unresolved `stepType` must fail validation before runtime execution when possible

Example module ownership:

- `MQ_MESSAGE` -> RabbitMQ adapter module
- `KAFKA_MESSAGE` -> Kafka adapter module
- `IM_GROUP_CREATE` -> IM adapter module
- `IM_GROUP_INVITE` -> IM adapter module
- `IM_GROUP_MESSAGE_SEND` -> IM adapter module
- `NOTIFY_IN_APP_SEND` -> notification adapter module
- `NOTIFY_SMS_SEND` -> notification adapter module
- `NOTIFY_EMAIL_SEND` -> notification adapter module
- `BEAN_INVOKE` -> local application module

This allows one action definition to span multiple modules while keeping the DSL stable.

## Target Routing

`target` identifies the concrete downstream platform, provider, or logical route used by the handler.

Examples:

- `IM_GROUP_CREATE` with `target: wecom`
- `IM_GROUP_INVITE` with `target: feishu`
- `NOTIFY_SMS_SEND` with `target: aliyun-sms`
- `NOTIFY_EMAIL_SEND` with `target: smtp`
- `MQ_MESSAGE` with `target: topic.user.created`

Recommended rule:

- `stepType` defines the capability contract
- `target` selects the concrete provider or destination inside that capability

This prevents the DSL from exploding into provider-specific step type names such as `WECOM_GROUP_INVITE` or `ALIYUN_SMS_SEND`.

## Step Handler Contract

Every registered `stepType` should implement a common execution contract.

Suggested shape:

```java
public interface ActionStepHandler {

    String stepType();

    StepExecutionResult execute(ActionStepContext context);
}
```

The framework runtime owns orchestration. The handler owns only the concrete side effect execution.

The handler must not:

- update action state directly
- manipulate outbox rows directly
- decide global orchestration transitions by itself

The handler should:

- execute the downstream operation
- return structured execution facts
- surface normalized failure information
- honor timeout and idempotency requirements

## Step Context

The execution context passed to handlers should include:

- action identity
- action name and version
- business key
- action attributes
- step definition
- attempt number
- resolved idempotency key
- timeout metadata
- prior step outputs if enabled by runtime policy

The DSL itself does not expose this structure directly, but templates and handlers rely on it as runtime input.

## Capability-Specific Input Shape

The first version should keep one unified runtime contract while allowing capability-specific request payloads.

Examples:

- `IM_GROUP_CREATE`: group name, owner, member list, optional avatar or metadata
- `IM_GROUP_INVITE`: group id, member list, inviter
- `IM_GROUP_MESSAGE_SEND`: group id, message type, rendered content
- `NOTIFY_IN_APP_SEND`: receiver ids, template id, variables
- `NOTIFY_SMS_SEND`: phone numbers, sign, template id, variables
- `NOTIFY_EMAIL_SEND`: recipients, subject, body or template variables

The framework should persist the rendered request snapshot before or alongside execution so governance can inspect exactly what was sent.

## Step Result Semantics

A handler result should let the runtime distinguish:

- success
- retryable failure
- terminal failure

The result should also optionally carry:

- output payload
- downstream receipt id
- normalized error code
- error message

This is necessary for retry policy, governance display, and audit quality.

## Request Mapping

Request mapping converts action context into step input.

Suggested sources:

- `bizKey`
- `attributes.*`
- previous step outputs if persisted and explicitly exposed
- runtime metadata such as `actionId`

The first version should support simple template substitution and map rendering. Complex scripting should be avoided.

## Retry Policy

Retry may be declared at action defaults level and overridden by individual steps.

Fields:

- `maxAttempts`
- `backoff.mode`: `FIXED` or `EXPONENTIAL`
- `backoff.delay` or `backoff.initialDelay`
- `backoff.maxDelay`
- `retryOn`: optional error code or exception classification list

Rules:

- step-local policy overrides action defaults
- absence of step-local policy falls back to defaults
- every retry must persist current attempt count and `nextRunAt`

## Timeout Policy

`timeout` defines the maximum runtime of one step attempt.

Rules:

- timeout breach is treated as a failed attempt
- timeout classification may still be retryable
- timeout must be visible in step history and alert context

## Step Idempotency

Step idempotency prevents duplicate side effects when dispatch or worker failures cause replay.

Fields:

- `keyTemplate`
- `mode`: `REQUIRED` or `BEST_EFFORT`

Rules:

- `REQUIRED` means the handler must reject execution if no deterministic key can be resolved
- handlers should propagate idempotency keys to downstream systems when possible

## Failure Policy

Failure policy controls what happens when retries are exhausted.

Suggested fields:

- `afterRetriesExhausted`: `WAIT_MANUAL`, `FAIL_ACTION`, or `START_COMPENSATION`
- `manualReasonTemplate`

Recommended default:

- if compensation is configured for at least one completed prior step, use `START_COMPENSATION`
- otherwise use `WAIT_MANUAL`

## Compensation Definition

Each step may define an optional compensation block.

Compensation fields:

- `stepType`
- `target`
- `request`
- `retry`
- `timeout`

Rules:

- compensation is only eligible if the forward step completed successfully
- compensation executes in reverse order of completed forward steps
- compensation has its own retry and timeout policy
- compensation outcome is durably stored and auditable
- compensation must also resolve to a registered executable handler contract

## Alert Policy

Alert policy may exist at action or step level.

Suggested fields:

- `onWaitingManual`
- `onFinalFailure`
- `onCompensationFailure`
- `onHighLatency`

Values may be severity levels such as `P1`, `P2`, `P3`.

## Runtime Context

The runtime context exposed to templates should include:

- `actionId`
- `actionName`
- `bizKey`
- `attributes`
- `currentStep`
- `attempt`
- `publishedAt`

The first version should avoid arbitrary code execution in templates.

## Validation Rules

The framework should reject a definition when:

- `name` is blank
- `steps` is empty
- step names are duplicated
- required fields for a step type are missing
- retry policy is internally contradictory
- compensation block is malformed
- unsupported step type is used without a registered handler

Startup validation should check not only the forward `stepType`, but also every compensation `stepType` referenced by enabled definitions.

## Versioning Rules

Recommended first-version behavior:

- new publishes use the latest enabled definition version unless explicitly pinned
- running action instances continue with the resolved version they started with
- definition version is persisted on the action instance

## Explicit Non-Features In This DSL

- no parallel steps
- no joins
- no nested subflows
- no embedded scripts
- no dynamic graph mutation during execution
