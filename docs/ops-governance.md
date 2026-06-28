# Ops Governance

## Purpose

Governance is a core framework capability, not an optional admin UI.

If asynchronous side effects can fail, repeat, compensate, or stall, operators need durable visibility and bounded control.

## Current Status

The governance layer now has a real backend API in `action-guard-ops-api` for:

- action list query
- action detail query
- step detail query
- consume detail query
- audit log query
- manual retry
- skip current step
- cancel action
- compensate entrypoint

This does **not** mean all governance semantics are fully mature.

Current boundaries:

- query APIs return real data from runtime tables backed by the configured JDBC store
- write operations write durable governance audit logs
- compensation entry is wired to a real compensation runtime path
- compensation execution is controlled by an action-level switch resolved from YAML default plus database override
- compensation execution writes step-level durable compensation logs
- skip is implemented with a minimal semantic: current step is moved into a stable success state and the audit log distinguishes operator skip from real execution success
- action, step, and outbox writes now share a unified optimistic-locking-based fencing rule via `version`
- permissions are not implemented in the current project phase

## Governance Goals

- make every action instance observable
- make failure states diagnosable
- make operator actions explicit and auditable
- prevent unsafe manual operations
- keep automation and human intervention on the same state model
- make repeated MQ consumption visible and explainable

## Primary Views

The ops layer should provide these first-version views.

These views are now implemented at API level in `action-guard-ops-api`.

### Action List

Fields:

- action id
- action name
- biz key
- status
- current step
- created time
- updated time
- last error code
- last error message

Common filters:

- status
- action name
- biz key
- creation time range

Current implementation notes:

- pagination is supported
- basic filters are supported
- "waiting manual only" is not currently implemented because the runtime state model does not yet expose a dedicated waiting-manual terminal path

### Action Detail

Fields:

- action base fields
- current failure reason
- step summary list
- consume summary list

Current implementation notes:

- resolved definition version is not currently exposed
- outbox state is not currently included in the detail response
- audit timeline is queried through a separate audit log endpoint
- compensation timeline is queried through a separate compensation log endpoint

### Step Detail

Fields:

- step name and type
- target
- attempt count
- last error code and message

Current implementation notes:

- timeout, retry policy, request/response snapshot, and compensation history are not yet exposed in the current response model

### Message Consumption Detail

Fields:

- message id
- consumer group
- consume status
- attempt count
- last consume failure reason

Current implementation notes:

- attempt count is the currently exposed proxy for delivery count
- dedicated dead-letter state exposure is not yet modeled separately in the governance response

## Current Operator Actions

The current API exposes a small but strict set of operator actions.

### Manual Retry

Use when the underlying problem is believed resolved.

Rules:

- allowed from `FAILED` or `RETRYING`
- writes a durable audit event
- reuses the existing current-step dispatch path

### Skip Current Step

Use only when business owners accept omission of the side effect.

Rules:

- currently does not require explicit reason input in the API contract
- current project phase does not support non-skippable step metadata
- writes an audit record and advances to the next step or `SUCCESS`
- current implementation marks the skipped step into a stable success state and relies on audit logs to preserve the operator-skip meaning

### Cancel Action

Stops further automatic execution.

Rules:

- allowed only for non-terminal actions
- does not pretend already completed side effects were undone
- currently moves the action to `IGNORED`

### Trigger Compensation

Starts reverse handling for already successful prior steps.

Rules:

- current implementation validates action status and records durable audit
- effective compensation enablement is resolved as: database override by `actionName` wins, otherwise fallback to YAML `compensationEnabled`
- YAML `compensationEnabled` default is currently `false`
- only `FAILED` and `DEAD` may enter compensation
- compensation runs only against already successful steps
- successful steps are compensated in reverse `stepIndex` order
- if no compensator is registered for a successful step, that step is skipped and compensation continues
- if any compensator fails, the action moves to `DEAD`
- if all compensations succeed or are skippable, the action moves to `COMPENSATED`
- one compensation run produces one `compensation_batch_id`
- each processed successful step writes one compensation log row

### Reopen Waiting Manual

Returns a manually handled action to dispatchable execution after policy or data correction.

## Safety Controls

Governance APIs must enforce safety, not just rely on UI warnings.

Recommended controls:

- optimistic version check on operator mutations
- action-level and step-level terminal-state guards

Current implementation notes:

- action-level state validation is implemented
- permission boundaries are intentionally not implemented in the current project phase
- explicit reason fields are not yet enforced
- non-skippable step markers are not yet implemented
- compensation is additionally guarded by an action-level governance switch
- governance write conflicts are surfaced explicitly rather than retried silently

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

Current implementation notes:

- alerting integration is not implemented yet

## Audit Requirements

Every operator action must create an immutable audit event.

Required audit context:

- who initiated the action
- when it happened
- what action and step were affected
- what was requested
- what state changed
- why the operator performed it

Current implementation notes:

- durable audit persistence is implemented through `action_ops_audit_log`
- current stored fields include action id, operation type, operator, request payload snapshot, result status, result message, and created time
- `operator` currently comes from optional request header `X-Action-Guard-Operator`, defaulting to `anonymous`
- compensate success and failure are both audited through the same governance audit pipeline

## Governance API Surface

Current API categories:

- list actions
- query action detail
- query step detail list
- query message consume detail list
- manual retry
- skip step
- cancel action
- trigger compensation
- query compensation logs
- list audit logs

The API contract is state-aware and returns clear rejection failures for invalid transitions or disabled capabilities.

Current endpoint groups:

- `GET /api/actions`
- `GET /api/actions/{actionInstanceId}`
- `GET /api/actions/{actionInstanceId}/steps`
- `GET /api/actions/{actionInstanceId}/consumes`
- `GET /api/actions/{actionInstanceId}/compensations`
- `GET /api/audit-logs`
- `POST /api/actions/{actionInstanceId}/retry`
- `POST /api/actions/{actionInstanceId}/skip`
- `POST /api/actions/{actionInstanceId}/cancel`
- `POST /api/actions/{actionInstanceId}/compensate`

Current compensation behavior:

- disabled switch: explicit failure + audit
- enabled switch + successful reverse compensation: success + audit
- enabled switch + compensation failure: failure + audit
- `SKIPPED / SUCCESS / FAILED` compensation step results are written to a dedicated compensation log table
- compensation state transitions are protected by optimistic locking; on conflict, the current node stops compensation and does not continue the batch

## Concurrency And Fencing

Current fencing rule:

- `action_instance`
- `action_step_instance`
- `action_outbox`

all use existing `version` fields as optimistic-locking guards.

Current conflict handling:

- runtime forward progression conflict:
  stop current progression and do not continue dispatch
- runtime retry progression conflict:
  stop current retry dispatch
- compensation progression conflict:
  stop the current compensation run
- governance write conflict:
  return explicit failure and write failed audit

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

- retries exhausted currently move action to `FAILED`
- skipping a step currently does not require operator reason
- compensation failure alerting is not implemented yet
- terminal actions cannot be mutated except by governance transitions explicitly allowed by current validation logic
- compensation is disabled by default unless YAML enables it or database policy overrides it to enabled

## Governance Persistence

Current governance persistence uses:

- runtime tables:
  - `action_instance`
  - `action_step_instance`
  - `action_outbox`
  - `action_consume_log`
- governance audit table:
  - `action_ops_audit_log`
- governance policy table:
  - `action_governance_policy`
- compensation log table:
  - `action_compensation_log`

Compensation log semantics:

- one compensation run creates one `compensation_batch_id`
- one processed successful historical step creates one log row in that batch
- current compensation statuses are:
  - `SKIPPED`
  - `SUCCESS`
  - `FAILED`

This allows governance queries and write-audit persistence to share the same JDBC store as the runtime prototype, with H2 as the default demo option and MySQL as a production switch target.

## Boundaries

Governance is not an excuse to normalize broken automation.

If most actions require manual intervention, the correct fix is runtime and policy improvement, not stronger operator tooling alone.
