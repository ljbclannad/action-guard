-- 升级应用前手工执行：新增独立可靠告警 Outbox，不修改既有执行 Outbox 语义。
create table if not exists action_alert_outbox
(
    id
    varchar
(
    64
) primary key,
    event_id varchar
(
    64
) not null,
    dedupe_key char
(
    64
) not null,
    type varchar
(
    64
) not null,
    level varchar
(
    32
) not null,
    title varchar
(
    256
) not null,
    message text,
    action_name varchar
(
    128
),
    action_instance_id varchar
(
    64
),
    step_name varchar
(
    128
),
    step_type varchar
(
    128
),
    occurred_at timestamp not null,
    details_json text,
    status varchar
(
    32
) not null,
    available_at timestamp not null,
    delivery_attempt_count int not null default 0,
    last_error_message varchar
(
    512
),
    version int not null default 0,
    created_at timestamp not null,
    updated_at timestamp not null,
    unique index uk_action_alert_outbox_event
(
    event_id
),
    unique index uk_action_alert_outbox_dedupe
(
    dedupe_key
),
    index idx_action_alert_outbox_recoverable
(
    status,
    available_at,
    created_at
),
    index idx_action_alert_outbox_claimed
(
    status,
    updated_at
),
    index idx_action_alert_outbox_action
(
    action_instance_id,
    created_at
)
    );
