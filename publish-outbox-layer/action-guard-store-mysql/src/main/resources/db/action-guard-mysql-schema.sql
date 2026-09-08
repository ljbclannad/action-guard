-- 初始化脚本：表和索引一同创建，重复执行时保留已有表及数据。
-- 已有表不会自动补齐字段、索引或注释；旧库升级需单独核对结构和存量数据后执行迁移。
-- 步骤唯一约束仅在新建表时生效；旧库迁移前须检查同一动作内是否存在重复步骤索引。

create table if not exists action_instance (
    id varchar(64) primary key comment 'Action 实例唯一标识',
    action_name varchar(128) not null comment '发布时采用的 Action 定义名称',
    definition_version int not null comment '发布时采用的定义版本，不是乐观锁版本',
    biz_key varchar(256) not null comment '关联业务记录的标识，例如订单号',
    idempotency_key varchar(256) not null comment '发布去重键，默认由动作名称与业务标识拼接，也可显式指定',
    status varchar(32) not null comment 'Action 整体执行状态，由状态机约束迁移',
    current_step_index int not null comment '从零开始的步骤指针，顺序推进完成后可等于总步骤数',
    total_step_count int not null comment '发布时从定义复制的总步骤数，不是已完成数',
    attributes_json text comment '动作级公共输入属性的 JSON',
    last_error_code varchar(128) comment '最近记录的错误码，可在状态迁移时清除',
    last_error_message text comment '最近记录的错误原因摘要',
    version int not null default 0 comment '持久化乐观锁版本，成功更新时递增',
    created_at timestamp not null comment '实例创建时间',
    updated_at timestamp not null comment '实例最近更新时间，供恢复扫描判断超时',
    index idx_action_instance_name_biz (action_name, biz_key),
    unique index uk_action_instance_idempotency (idempotency_key)
) comment = 'Action 执行实例及整体进度';

create table if not exists action_step_instance (
    id varchar(64) primary key comment '步骤实例唯一标识',
    action_instance_id varchar(64) not null comment '所属 Action 实例标识',
    step_index int not null comment '从零开始的步骤索引，同一 Action 内唯一',
    step_name varchar(128) not null comment '发布时从定义复制的步骤名称',
    step_type varchar(128) not null comment '步骤类型，运行时据此查找 Handler',
    target varchar(256) not null comment '执行目标，由具体 Handler 解释',
    status varchar(32) not null comment '步骤状态：PENDING、RUNNING、SUCCESS 或 FAILED',
    attempt_count int not null comment '已记录的执行尝试次数，包含首次执行，结果落库时递增',
    payload_json text comment '当前步骤输入载荷的 JSON，与动作级属性分开存储',
    last_error_code varchar(128) comment '最近一次失败的错误码，成功时清除',
    last_error_message text comment '最近一次失败的原因摘要，成功时清除',
    version int not null default 0 comment '持久化乐观锁版本，成功更新时递增',
    created_at timestamp not null comment '步骤实例创建时间',
    updated_at timestamp not null comment '步骤实例最近更新时间',
    unique index uk_action_step_instance_action_step (action_instance_id, step_index)
) comment = 'Action 中的步骤执行实例';

create table if not exists action_outbox (
    id varchar(64) primary key comment 'Outbox 记录唯一标识',
    action_instance_id varchar(64) not null comment '所属 Action 实例标识',
    topic varchar(64) not null comment '执行消息的逻辑主题',
    dispatch_id varchar(64) not null comment '逻辑投递标识，传输重发保留，步骤推进或业务重试时重新生成',
    status varchar(32) not null comment '发布状态：NEW、CLAIMED、DONE 或 DEAD；DONE 不代表消费完成',
    available_at timestamp not null comment '任务可调度时间，用于延迟重试和恢复扫描',
    attempt_count int not null comment '当前投递失败回退和业务重试调度均递增的累计计数，不是纯 MQ 失败次数',
    version int not null default 0 comment '持久化乐观锁版本，用于投递抢占与并发检测',
    created_at timestamp not null comment '记录创建时间，后续调度保留',
    updated_at timestamp not null comment '最近更新时间，用于判断抢占是否超时',
    index idx_action_outbox_action (action_instance_id),
    index idx_action_outbox_recoverable (status, available_at, created_at)
) comment = '执行消息的可靠投递记录';

create table if not exists action_consume_log (
    id varchar(64) primary key comment '消费记录唯一标识',
    message_id varchar(128) not null comment '消息唯一标识，当前按此标识抢占和去重，不按消费组分隔',
    action_instance_id varchar(64) not null comment '消息关联的 Action 实例标识',
    consumer_group varchar(128) not null comment '处理消息的消费组标识',
    consume_status varchar(32) not null comment '消息消费状态，与 Action 和步骤状态独立',
    dedupe_key varchar(128) not null comment '从消息 messageKey 复制的去重关联信息，不是发布幂等键',
    attempt_count int not null comment '首次为一，失败后重新抢占或标记重复跳过时递增，不是 Handler 执行次数',
    last_error_message text comment '最近记录的消费错误或重复跳过原因',
    version int not null default 0 comment '持久化乐观锁版本，用于并发更新检测',
    first_received_at timestamp not null comment '首次创建消费记录的时间',
    last_received_at timestamp not null comment '最近接收或处理时间，状态更新也会刷新',
    updated_at timestamp not null comment '消费记录最近更新时间',
    unique index uk_action_consume_log_message (message_id),
    index idx_action_consume_log_action (action_instance_id)
) comment = '执行消息的消费抢占、去重及处理记录';

create table if not exists action_ops_audit_log (
    id varchar(64) primary key comment '审计日志唯一标识',
    action_instance_id varchar(64) not null comment '治理操作涉及的 Action 实例标识',
    operation_type varchar(64) not null comment '治理操作类型，例如重试、跳过、取消或补偿',
    operator varchar(128) not null comment '执行治理操作的操作人标识',
    request_payload_json text comment '治理操作请求内容的 JSON',
    result_status varchar(32) not null comment '治理操作的处理结果，不是 Action 执行状态',
    result_message text comment '治理操作结果说明或失败原因',
    created_at timestamp not null comment '审计日志创建时间',
    index idx_action_ops_audit_log_action (action_instance_id, created_at),
    index idx_action_ops_audit_log_operator (operator, created_at)
) comment = '人工治理操作审计日志';

create table if not exists action_governance_policy (
    id varchar(64) primary key comment '治理策略唯一标识',
    action_name varchar(128) not null comment '策略对应的 Action 定义名称，每个名称一条策略',
    compensation_enabled tinyint null comment '补偿开关覆盖值：一为开启、零为关闭、空值回退定义；不表示自动补偿',
    retry_policy_json text comment '重试策略 JSON，当前仅持久化，未接入运行时决策',
    alert_policy_json text comment '告警策略 JSON，当前仅持久化，未接入运行时决策',
    updated_at timestamp not null comment '策略最近更新时间',
    unique index uk_action_governance_policy_name (action_name)
) comment = '按 Action 名称配置的治理策略';

create table if not exists action_compensation_log (
    id varchar(64) primary key comment '补偿日志唯一标识',
    compensation_batch_id varchar(64) not null comment '补偿批次标识，当前由 batch- 与 Action 实例标识拼接',
    action_instance_id varchar(64) not null comment '所属 Action 实例标识',
    action_step_instance_id varchar(64) not null comment '被补偿的步骤实例标识',
    step_index int not null comment '原流程中从零开始的步骤索引，补偿按已成功步骤倒序执行',
    step_name varchar(128) not null comment '被补偿步骤的名称',
    step_type varchar(128) not null comment '被补偿步骤的类型',
    compensation_status varchar(32) not null comment '本条补偿结果：SUCCESS、FAILED 或 SKIPPED',
    compensator_name varchar(256) comment '补偿器实现类全限定名，无补偿器而跳过时为空',
    result_message text comment '补偿结果说明或跳过原因',
    created_at timestamp not null comment '补偿日志创建时间',
    updated_at timestamp not null comment '日志更新时间，当前新增时与创建时间相同',
    index idx_action_compensation_log_action (action_instance_id, created_at),
    index idx_action_compensation_log_batch (compensation_batch_id, step_index)
) comment = '步骤补偿结果及中断恢复依据';

create table if not exists action_transition_log (
    id varchar(64) primary key comment '迁移日志唯一标识',
    action_instance_id varchar(64) not null comment '发生状态迁移的 Action 实例标识',
    transition_event varchar(64) not null comment '触发本次状态迁移的事件',
    from_status varchar(32) not null comment '迁移前的 Action 状态',
    to_status varchar(32) not null comment '迁移后的 Action 状态',
    step_index int comment '关联步骤的零起始索引，无步骤上下文时为空',
    step_name varchar(128) comment '关联步骤名称，无步骤上下文时为空',
    step_type varchar(128) comment '关联步骤类型，无步骤上下文时为空',
    operator varchar(128) comment '操作人标识，自动迁移等未提供操作人时为空',
    error_code varchar(128) comment '本次迁移关联的错误码',
    error_message text comment '本次迁移关联的错误原因摘要',
    created_at timestamp not null comment '迁移日志记录的时间',
    index idx_action_transition_log_action (action_instance_id, created_at)
) comment = 'Action 状态迁移时间线';
