package io.github.actionguard.store.mysql;

import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.model.ActionTransitionLog;
import io.github.actionguard.core.repository.ActionTransitionLogRepository;
import io.github.actionguard.core.runtime.state.ActionTransitionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MysqlActionTransitionLogRepositoryTest {

    @Test
    void shouldPersistAndLoadTransitionLogs() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:transition_log;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
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
                )
                """);

        ActionTransitionLogRepository repository = new MysqlActionTransitionLogRepository(jdbcTemplate);
        Instant now = Instant.parse("2026-06-26T12:00:00Z");
        repository.save(new ActionTransitionLog(
                "transition-1",
                "act-1",
                ActionTransitionEvent.STEP_SUCCEEDED,
                ActionStatus.NEW,
                ActionStatus.DISPATCHING,
                0,
                "send-user-sms",
                "SMS",
                null,
                null,
                null,
                now
        ));

        assertThat(repository.findByActionInstanceId("act-1")).hasSize(1);
        assertThat(repository.findByActionInstanceId("act-1").get(0).event()).isEqualTo(ActionTransitionEvent.STEP_SUCCEEDED);
        assertThat(repository.findByActionInstanceId("act-1").get(0).toStatus()).isEqualTo(ActionStatus.DISPATCHING);
    }
}
