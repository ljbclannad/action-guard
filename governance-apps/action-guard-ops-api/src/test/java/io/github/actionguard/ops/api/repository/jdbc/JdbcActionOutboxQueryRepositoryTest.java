package io.github.actionguard.ops.api.repository.jdbc;

import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.ops.api.model.ActionOutboxView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcActionOutboxQueryRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcActionOutboxQueryRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:outbox_query;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("drop table if exists action_outbox");
        jdbcTemplate.execute("""
                create table action_outbox (
                    id varchar(64) primary key,
                    action_instance_id varchar(64) not null,
                    topic varchar(64) not null,
                    dispatch_id varchar(64) not null,
                    status varchar(32) not null,
                    available_at timestamp not null,
                    attempt_count int not null,
                    delivery_attempt_count int not null,
                    version int not null,
                    created_at timestamp not null,
                    updated_at timestamp not null
                )
                """);
        repository = new JdbcActionOutboxQueryRepository(jdbcTemplate);
    }

    @Test
    void shouldReturnOrderedDiagnosticsForRequestedActionOnly() {
        Instant earlier = Instant.parse("2026-06-26T11:00:00Z");
        Instant later = Instant.parse("2026-06-26T12:00:00Z");
        insert("outbox-b", "act-1", "dispatch-b", "DONE", later, 8, 3, 7);
        insert("outbox-a", "act-1", "dispatch-a", "NEW", earlier, 2, 1, 4);
        insert("outbox-other", "act-2", "dispatch-other", "DEAD", earlier, 1, 1, 1);

        var outboxes = repository.findByActionInstanceId("act-1");

        assertThat(outboxes).hasSize(2);
        assertThat(outboxes).extracting(ActionOutboxView::id).containsExactly("outbox-a", "outbox-b");
        assertThat(outboxes.get(0).status()).isEqualTo(ActionOutboxStatus.NEW);
        assertThat(outboxes.get(0).attemptCount()).isEqualTo(2);
        assertThat(outboxes.get(0).deliveryAttemptCount()).isEqualTo(1);
        assertThat(outboxes.get(1).status()).isEqualTo(ActionOutboxStatus.DONE);
        assertThat(outboxes.get(1).dispatchId()).isEqualTo("dispatch-b");
        assertThat(jdbcTemplate.queryForObject("select count(*) from action_outbox", Integer.class)).isEqualTo(3);
    }

    @Test
    void shouldReturnEmptyListWhenActionHasNoOutbox() {
        assertThat(repository.findByActionInstanceId("missing")).isEmpty();
    }

    private void insert(
            String id, String actionInstanceId, String dispatchId, String status,
            Instant createdAt, int attemptCount, int deliveryAttemptCount, int version
    ) {
        jdbcTemplate.update(
                "insert into action_outbox values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, actionInstanceId, "ACTION_EXECUTE", dispatchId, status,
                Timestamp.from(createdAt.plusSeconds(30)), attemptCount, deliveryAttemptCount, version,
                Timestamp.from(createdAt), Timestamp.from(createdAt.plusSeconds(60))
        );
    }
}
