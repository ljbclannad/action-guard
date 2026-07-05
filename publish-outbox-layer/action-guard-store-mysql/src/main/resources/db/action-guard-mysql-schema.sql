create table if not exists action_instance (
    id varchar(64) primary key,
    action_name varchar(128) not null,
    biz_key varchar(256) not null,
    status varchar(32) not null,
    current_step_index int not null,
    total_step_count int not null,
    attributes_json text,
    last_error_code varchar(128),
    last_error_message text,
    version int not null default 0,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_action_instance_name_biz on action_instance (action_name, biz_key);

create table if not exists action_step_instance (
    id varchar(64) primary key,
    action_instance_id varchar(64) not null,
    step_index int not null,
    step_name varchar(128) not null,
    step_type varchar(128) not null,
    target varchar(256) not null,
    status varchar(32) not null,
    attempt_count int not null,
    payload_json text,
    last_error_code varchar(128),
    last_error_message text,
    version int not null default 0,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_action_step_instance_action on action_step_instance (action_instance_id, step_index);

create table if not exists action_outbox (
    id varchar(64) primary key,
    action_instance_id varchar(64) not null,
    topic varchar(64) not null,
    status varchar(32) not null,
    available_at timestamp not null,
    attempt_count int not null,
    version int not null default 0,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_action_outbox_action on action_outbox (action_instance_id);

create table if not exists action_consume_log (
    id varchar(64) primary key,
    message_id varchar(128) not null,
    action_instance_id varchar(64) not null,
    consumer_group varchar(128) not null,
    consume_status varchar(32) not null,
    dedupe_key varchar(128) not null,
    attempt_count int not null,
    last_error_message text,
    version int not null default 0,
    first_received_at timestamp not null,
    last_received_at timestamp not null,
    updated_at timestamp not null
);

create unique index uk_action_consume_log_message on action_consume_log (message_id);
create index idx_action_consume_log_action on action_consume_log (action_instance_id);

create table if not exists action_ops_audit_log (
    id varchar(64) primary key,
    action_instance_id varchar(64) not null,
    operation_type varchar(64) not null,
    operator varchar(128) not null,
    request_payload_json text,
    result_status varchar(32) not null,
    result_message text,
    created_at timestamp not null
);

create index idx_action_ops_audit_log_action on action_ops_audit_log (action_instance_id, created_at);
create index idx_action_ops_audit_log_operator on action_ops_audit_log (operator, created_at);

create table if not exists action_governance_policy (
    id varchar(64) primary key,
    action_name varchar(128) not null,
    compensation_enabled tinyint null,
    retry_policy_json text,
    alert_policy_json text,
    updated_at timestamp not null
);

create unique index uk_action_governance_policy_name on action_governance_policy (action_name);

create table if not exists action_compensation_log (
    id varchar(64) primary key,
    compensation_batch_id varchar(64) not null,
    action_instance_id varchar(64) not null,
    action_step_instance_id varchar(64) not null,
    step_index int not null,
    step_name varchar(128) not null,
    step_type varchar(128) not null,
    compensation_status varchar(32) not null,
    compensator_name varchar(256),
    result_message text,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_action_compensation_log_action on action_compensation_log (action_instance_id, created_at);
create index idx_action_compensation_log_batch on action_compensation_log (compensation_batch_id, step_index);

create table if not exists action_transition_log (
    id varchar(64) primary key,
    action_instance_id varchar(64) not null,
    transition_event varchar(64) not null,
    from_status varchar(32) not null,
    to_status varchar(32) not null,
    step_index int,
    step_name varchar(128),
    step_type varchar(128),
    operator varchar(128),
    error_code varchar(128),
    error_message text,
    created_at timestamp not null
);

create index idx_action_transition_log_action on action_transition_log (action_instance_id, created_at);
