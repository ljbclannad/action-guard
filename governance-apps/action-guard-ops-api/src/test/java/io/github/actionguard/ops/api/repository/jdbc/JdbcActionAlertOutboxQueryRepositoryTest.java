package io.github.actionguard.ops.api.repository.jdbc;

import io.github.actionguard.core.model.ActionAlertOutboxStatus;
import io.github.actionguard.ops.api.model.ActionAlertOutboxView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcActionAlertOutboxQueryRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcActionAlertOutboxQueryRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:alert_outbox_query;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("drop table if exists action_alert_outbox");
        jdbcTemplate.execute("""
                create table action_alert_outbox (
                    id varchar(64) primary key,
                    event_id varchar(64) not null,
                    type varchar(64) not null,
                    level varchar(32) not null,
                    action_name varchar(128),
                    action_instance_id varchar(64),
                    step_name varchar(128),
                    step_type varchar(128),
                    status varchar(32) not null,
                    available_at timestamp not null,
                    delivery_attempt_count int not null,
                    last_error_message varchar(512),
                    occurred_at timestamp not null,
                    created_at timestamp not null,
                    updated_at timestamp not null,
                    version int not null
                )
                """);
        repository = new JdbcActionAlertOutboxQueryRepository(jdbcTemplate);
    }

    @Test
    void shouldReturnOrderedAlertDiagnosticsForRequestedActionOnly() {
        Instant earlier = Instant.parse("2026-06-26T11:00:00Z");
        Instant later = Instant.parse("2026-06-26T12:00:00Z");
        insert("alert-b", "event-b", "act-1", "DONE", later, 3, null, 7);
        insert("alert-a", "event-a", "act-1", "NEW", earlier, 1, "token=[REDACTED]", 4);
        insert("alert-other", "event-other", "act-2", "DEAD", earlier, 10, "failed", 1);

        var outboxes = repository.findByActionInstanceId("act-1");

        assertThat(outboxes).hasSize(2);
        assertThat(outboxes).extracting(ActionAlertOutboxView::id).containsExactly("alert-a", "alert-b");
        assertThat(outboxes.get(0).eventId()).isEqualTo("event-a");
        assertThat(outboxes.get(0).status()).isEqualTo(ActionAlertOutboxStatus.NEW);
        assertThat(outboxes.get(0).deliveryAttemptCount()).isEqualTo(1);
        assertThat(outboxes.get(0).lastErrorMessage()).isEqualTo("token=[REDACTED]");
        assertThat(outboxes.get(1).status()).isEqualTo(ActionAlertOutboxStatus.DONE);
        assertThat(outboxes.get(1).version()).isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject("select count(*) from action_alert_outbox", Integer.class)).isEqualTo(3);
    }

    @Test
    void shouldReturnEmptyListWhenActionHasNoAlertOutbox() {
        assertThat(repository.findByActionInstanceId("missing")).isEmpty();
    }

    private void insert(
            String id, String eventId, String actionInstanceId, String status, Instant createdAt,
            int deliveryAttemptCount, String lastErrorMessage, int version
    ) {
        jdbcTemplate.update(
                "insert into action_alert_outbox values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, eventId, "RETRIES_EXHAUSTED", "ERROR", "order-flow", actionInstanceId, "notify", "SMS", status,
                Timestamp.from(createdAt.plusSeconds(30)), deliveryAttemptCount, lastErrorMessage, Timestamp.from(createdAt),
                Timestamp.from(createdAt), Timestamp.from(createdAt.plusSeconds(60)), version
        );
    }
}
