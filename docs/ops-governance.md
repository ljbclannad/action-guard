# Ops Governance

## Purpose

Governance is a core framework capability, not an optional admin UI.

If asynchronous side effects can fail, repeat, compensate, or stall, operators need durable visibility and bounded control.

## Governance Goals

- make every action instance observable
- make failure states diagnosable
- make operator actions explicit and auditable
- prevent unsafe manual operations
- keep automation and human intervention on the same state model
- make repeated MQ consumption visible and explainable

## Primary Views

The ops layer should provide these first-version views:

### Action List

Fields:

- action id
- action name
- biz key
- status
- current step
- next run time
- created time
- updated time

Common filters:

- status
- action name
- biz key
- creation time range
- waiting manual only
- compensation related only

### Action Detail

Fields:

- resolved definition version
- attributes snapshot
- current failure reason
- current retry state
- compensation state
- outbox state
- full step timeline
- audit timeline

### Step Detail

Fields:

- step name and type
- target
- attempt count
- timeout
- retry policy
- last request and response snapshot
- last error code and message
- compensation history

### Message Consumption Detail

Fields:

- message id
- consumer group
- consume status
- delivery count
- duplicate skipped or not
- last consume failure reason
- dead-letter state if any

## Standard Operator Actions

The first version should define a small but strict set of actions.

### Manual Retry

Use when the underlying problem is believed resolved.

Rules:

- allowed from `WAITING_MANUAL`, `FAILED`, or compensation-failed states
- increments an operator-triggered retry counter or writes an audit event
- resets the next executable state to dispatchable

### Skip Current Step

Use only when business owners accept omission of the side effect.

Rules:

- must require reason input
- should be disabled for steps marked non-skippable
- writes an audit record and advances to the next step

### Cancel Action

Stops further automatic execution.

Rules:

- allowed only for non-terminal actions
- must not pretend already completed side effects were undone
- may optionally trigger compensation if configured and approved

### Trigger Compensation

Starts reverse handling for already successful prior steps.

Rules:

- must run in reverse successful-step order
- should require explicit confirmation when invoked manually
- writes durable audit records for each compensation attempt

### Reopen Waiting Manual

Returns a manually handled action to dispatchable execution after policy or data correction.

## Safety Controls

Governance APIs must enforce safety, not just rely on UI warnings.

Recommended controls:

- optimistic version check on operator mutations
- role-based permission boundaries
- explicit reason fields for destructive actions
- action-level and step-level terminal-state guards
- non-skippable step markers

## Alerting

Alerting should be attached to meaningful operational transitions.

Minimum first-version alert events:

- action enters `WAITING_MANUAL`
- retries exhausted
- compensation fails
- dispatcher lag exceeds threshold
- action age exceeds SLA
- repeated consume failure exceeds threshold
- dead-letter backlog exceeds threshold

Recommended alert payload:

- action id
- action name
- biz key
- current step
- status
- last error code
- last error summary
- ops deep link if available

## Audit Requirements

Every operator action must create an immutable audit event.

Required audit context:

- who initiated the action
- when it happened
- what action and step were affected
- what was requested
- what state changed
- why the operator performed it

## Governance API Surface

Suggested first-version API categories:

- list actions
- query action detail
- query step detail
- query message consume detail
- manual retry
- skip step
- cancel action
- trigger compensation
- list audit logs

The API contract should be state-aware and return clear rejection reasons for invalid transitions.

## SLO And Monitoring

Recommended operational metrics:

- action publish count
- action success count
- action failure count
- action waiting manual count
- step retry count
- compensation count
- dispatcher scan latency
- dispatcher claim failure count
- MQ publish failure count
- MQ redelivery count
- duplicate consume skipped count
- dead-letter message count
- step execution latency by step type

Recommended dashboards:

- backlog and dispatch lag
- MQ publish and consume health
- duplicate consumption trend
- dead-letter backlog
- retries and exhausted retries
- waiting manual trend
- compensation trend
- terminal failure distribution by action and step

## Governance Policy Defaults

Reasonable first-version defaults:

- retries exhausted moves action to `WAITING_MANUAL` unless policy says start compensation
- skipping a step requires operator reason
- compensation failure always triggers `P1` or `P2` alert
- terminal actions cannot be mutated except by explicit governance transitions defined by policy

## Definition-Time Governance Hooks

The DSL should allow selected governance hints:

- step skippable or not
- alert severity overrides
- compensation required or optional
- manual intervention policy after retries exhausted

This keeps runtime operations predictable while still allowing business-specific policy differences.

## Boundaries

Governance is not an excuse to normalize broken automation.

If most actions require manual intervention, the correct fix is runtime and policy improvement, not stronger operator tooling alone.
