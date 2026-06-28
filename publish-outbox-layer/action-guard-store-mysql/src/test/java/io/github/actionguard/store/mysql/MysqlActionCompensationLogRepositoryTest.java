package io.github.actionguard.store.mysql;

import io.github.actionguard.core.model.ActionCompensationLog;
import io.github.actionguard.core.repository.ActionCompensationLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MysqlActionCompensationLogRepositoryTest {

    @Test
    void shouldPersistAndLoadCompensationLogs() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:compensation_log;MODE=MySQL;DB_CLOSE_DELAY=-1");
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

        ActionCompensationLogRepository repository = new MysqlActionCompensationLogRepository(jdbcTemplate);
        Instant now = Instant.parse("2026-06-26T12:00:00Z");
        repository.save(new ActionCompensationLog(
                "log-1",
                "batch-1",
                "act-1",
                "step-1",
                1,
                "send-user-sms",
                "SMS",
                "SUCCESS",
                "SmsCompensator",
                "ok",
                now,
                now
        ));

        assertThat(repository.findByActionInstanceId("act-1")).hasSize(1);
        assertThat(repository.findByActionInstanceId("act-1").get(0).compensationBatchId()).isEqualTo("batch-1");
        assertThat(repository.findByActionInstanceId("act-1").get(0).compensationStatus()).isEqualTo("SUCCESS");
    }
}
