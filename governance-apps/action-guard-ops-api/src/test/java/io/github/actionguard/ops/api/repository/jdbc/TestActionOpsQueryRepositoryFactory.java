package io.github.actionguard.ops.api.repository.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;

final class TestActionOpsQueryRepositoryFactory {

    private TestActionOpsQueryRepositoryFactory() {
    }

    static JdbcActionOpsQueryRepository createWithSeedData() {
        DataSource dataSource = dataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
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
                )
                """);
        jdbcTemplate.execute("""
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
                )
                """);
        jdbcTemplate.execute("""
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
                )
                """);

        Instant now = Instant.parse("2026-06-26T12:00:00Z");
        jdbcTemplate.update(
                "insert into action_instance (id, action_name, biz_key, status, current_step_index, total_step_count, attributes_json, last_error_code, last_error_message, version, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "act-1", "order-cancel-flow", "order:1", "SUCCESS", 2, 2, "{}", null, null, 0, Timestamp.from(now), Timestamp.from(now)
        );
        jdbcTemplate.update(
                "insert into action_step_instance (id, action_instance_id, step_index, step_name, step_type, target, status, attempt_count, payload_json, last_error_code, last_error_message, version, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "step-1", "act-1", 0, "send-cancel-event", "MQ_MESSAGE", "order.cancel.exchange", "SUCCESS", 1, "{}", null, null, 0, Timestamp.from(now), Timestamp.from(now)
        );
        jdbcTemplate.update(
                "insert into action_step_instance (id, action_instance_id, step_index, step_name, step_type, target, status, attempt_count, payload_json, last_error_code, last_error_message, version, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "step-2", "act-1", 1, "send-user-sms", "SMS", "notify.user", "SUCCESS", 1, "{}", null, null, 0, Timestamp.from(now), Timestamp.from(now)
        );
        jdbcTemplate.update(
                "insert into action_consume_log (id, message_id, action_instance_id, consumer_group, consume_status, dedupe_key, attempt_count, last_error_message, version, first_received_at, last_received_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "consume-1", "ACTION_EXECUTE:outbox-1", "act-1", "action-guard-demo", "ACKED", "ACTION_EXECUTE:outbox-1", 1, null, 0, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now)
        );
        return new JdbcActionOpsQueryRepository(jdbcTemplate);
    }

    private static DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:ops_query;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
