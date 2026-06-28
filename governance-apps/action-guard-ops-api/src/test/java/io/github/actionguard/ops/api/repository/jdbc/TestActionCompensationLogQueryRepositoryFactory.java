package io.github.actionguard.ops.api.repository.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;

final class TestActionCompensationLogQueryRepositoryFactory {

    private TestActionCompensationLogQueryRepositoryFactory() {
    }

    static JdbcActionCompensationLogQueryRepository createWithSeedData() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:compensation_log_query;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
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
                )
                """);
        Instant now = Instant.parse("2026-06-26T12:00:00Z");
        jdbcTemplate.update(
                "insert into action_compensation_log (id, compensation_batch_id, action_instance_id, action_step_instance_id, step_index, step_name, step_type, compensation_status, compensator_name, result_message, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "log-1", "batch-1", "act-1", "step-2", 1, "send-user-sms", "SMS", "SUCCESS", "SmsCompensator", "ok", Timestamp.from(now), Timestamp.from(now)
        );
        jdbcTemplate.update(
                "insert into action_compensation_log (id, compensation_batch_id, action_instance_id, action_step_instance_id, step_index, step_name, step_type, compensation_status, compensator_name, result_message, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "log-2", "batch-1", "act-1", "step-1", 0, "send-cancel-event", "MQ_MESSAGE", "SKIPPED", null, "no compensator registered", Timestamp.from(now), Timestamp.from(now)
        );
        return new JdbcActionCompensationLogQueryRepository(jdbcTemplate);
    }
}
