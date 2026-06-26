# Data Model

## Goal

The data model must make the framework recoverable, governable, and cluster-safe.

Every state transition that matters operationally must be durable and queryable.

## Core Tables

The first version should standardize four core tables:

- `action_instance`
- `action_step_instance`
- `action_outbox`
- `action_audit_log`
- `action_consume_log`

Optional supporting tables may be added later for definition registry caching, alert delivery records, or operator identities.

## action_instance

Represents one published action execution.

Suggested fields:

| Field | Purpose |
| --- | --- |
| `id` | primary key |
| `action_name` | definition name |
| `definition_version` | resolved definition version |
| `biz_key` | business identity |
| `status` | current action status |
| `current_step_index` | zero-based current step pointer |
| `total_step_count` | copied from definition for query efficiency |
| `retrying_step_index` | current retrying step when applicable |
| `attributes_json` | action input attributes |
| `idempotency_key` | publish deduplication key |
| `compensation_status` | current compensation phase |
| `next_run_at` | next schedulable execution time |
| `last_error_code` | normalized failure classification |
| `last_error_message` | summarized failure reason |
| `created_at` | creation timestamp |
| `updated_at` | last state update timestamp |
| `finished_at` | terminal completion timestamp |

Indexes:

- unique index on `idempotency_key` when action-level deduplication is enabled
- query index on `(status, next_run_at)`
- query index on `(action_name, biz_key)`
- query index on `created_at`

## action_step_instance

Represents durable execution state for each step of an action.

Suggested fields:

| Field | Purpose |
| --- | --- |
| `id` | primary key |
| `action_instance_id` | owning action |
| `step_index` | serial order index |
| `step_name` | step identifier |
| `step_type` | handler type |
| `target` | handler target |
| `status` | current step status |
| `attempt_count` | total forward attempts |
| `max_attempts` | copied resolved retry policy |
| `next_run_at` | next retry time |
| `timeout_ms` | resolved timeout |
| `idempotency_key` | resolved step idempotency key |
| `request_payload_json` | rendered request snapshot |
| `response_payload_json` | optional result snapshot |
| `last_error_code` | normalized failure classification |
| `last_error_message` | summarized failure reason |
| `started_at` | current or first start time |
| `finished_at` | final completion time |
| `updated_at` | last mutation timestamp |

Indexes:

- unique index on `(action_instance_id, step_index)`
- query index on `(status, next_run_at)`
- query index on `idempotency_key`

## action_outbox

Represents dispatchable runtime work.

This table is the durable bridge between business commit and async execution.

Suggested fields:

| Field | Purpose |
| --- | --- |
| `id` | primary key |
| `action_instance_id` | owning action |
| `topic` | logical work type such as `ACTION_EXECUTE` or `ACTION_COMPENSATE` |
| `status` | `NEW`, `CLAIMED`, `DONE`, `DEAD` |
| `available_at` | earliest dispatch time |
| `lease_owner` | worker or node identity |
| `lease_expires_at` | claim expiration |
| `attempt_count` | dispatch attempts |
| `last_error_message` | dispatcher-level failure reason |
| `created_at` | creation timestamp |
| `updated_at` | last mutation timestamp |

Indexes:

- query index on `(status, available_at)`
- query index on `(lease_expires_at)`
- query index on `action_instance_id`

Rules:

- inserting `action_outbox` must occur in the same transaction as `action_instance`
- a claimed row is safe to re-claim after lease expiry
- `DONE` means the dispatcher-side work item has been durably consumed, not necessarily that the action has fully succeeded

## action_consume_log

Represents message-layer consumption state for execution messages.

This table exists to make repeated consumption explicit and governable.

Suggested fields:

| Field | Purpose |
| --- | --- |
| `id` | primary key |
| `message_id` | stable MQ execution message id |
| `action_instance_id` | owning action |
| `step_instance_id` | optional step reference |
| `consumer_group` | logical consumer identity |
| `consume_status` | `RECEIVED`, `EXECUTING`, `ACKED`, `DUPLICATE_SKIPPED`, `FAILED`, `DEAD_LETTERED` |
| `dedupe_key` | repeated-consumption fence key |
| `attempt_count` | consumer-side redelivery count |
| `last_error_message` | last consume failure reason |
| `first_received_at` | first delivery time |
| `last_received_at` | most recent delivery time |
| `updated_at` | last mutation timestamp |

Indexes:

- unique index on `message_id`
- query index on `(consume_status, last_received_at)`
- query index on `action_instance_id`
- query index on `dedupe_key`

Rules:

- one execution message must have one stable `message_id`
- duplicate deliveries update consume history rather than creating invisible behavior
- `DUPLICATE_SKIPPED` should be queryable for diagnostics

## action_audit_log

Records governance-significant events.

Suggested fields:

| Field | Purpose |
| --- | --- |
| `id` | primary key |
| `action_instance_id` | owning action |
| `step_instance_id` | optional associated step |
| `event_type` | event category |
| `event_source` | `SYSTEM`, `OPERATOR`, `DISPATCHER`, `RUNTIME` |
| `operator_id` | optional human operator |
| `message` | human-readable summary |
| `details_json` | structured event payload |
| `created_at` | event timestamp |

Typical events:

- published
- claimed
- mq_published
- consume_received
- consume_duplicate_skipped
- step_started
- step_succeeded
- retry_scheduled
- retries_exhausted
- entered_waiting_manual
- compensation_started
- compensation_succeeded
- operator_retry
- operator_skip
- operator_cancel

## State Transition Constraints

`action_instance.status` should transition only through well-defined paths.

Examples:

- `PENDING -> DISPATCHING`
- `DISPATCHING -> SUCCESS`
- `DISPATCHING -> WAITING_RETRY`
- `DISPATCHING -> WAITING_MANUAL`
- `WAITING_MANUAL -> DISPATCHING`
- `WAITING_MANUAL -> CANCELLED`
- `DISPATCHING -> COMPENSATING`
- `COMPENSATING -> COMPENSATED`
- `COMPENSATING -> WAITING_MANUAL`

The schema alone does not enforce all transitions, but the runtime and governance APIs must.

## Message Consumption State Constraints

`action_consume_log.consume_status` should follow bounded transitions such as:

- `RECEIVED -> EXECUTING`
- `EXECUTING -> ACKED`
- `RECEIVED -> DUPLICATE_SKIPPED`
- `EXECUTING -> FAILED`
- `FAILED -> RECEIVED`
- `FAILED -> DEAD_LETTERED`

The message layer must not acknowledge a message as successful until the runtime result and consume state are durably recorded.

## Definition Persistence Strategy

The first version may choose one of two modes:

- file-backed definitions with resolved version copied to runtime tables
- persistent definition registry table introduced later

For the initial slice, file-backed definitions are acceptable if:

- action instance stores the resolved definition version
- rendered step metadata is snapshotted into `action_step_instance`
- running instances do not depend on mutating YAML files

## Retention And Archival

Recommended lifecycle:

- active tables retain recent operational data
- completed historical rows are archived by time window
- audit logs are retained longer than hot outbox rows

The archival process must preserve the ability to answer:

- what action ran
- what steps executed
- why it failed or compensated
- what operator actions were taken
