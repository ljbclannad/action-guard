alter table action_outbox
    add column delivery_attempt_count int not null default 0 comment '仅消息发送失败次数，达到上限后 Outbox 进入 DEAD';
