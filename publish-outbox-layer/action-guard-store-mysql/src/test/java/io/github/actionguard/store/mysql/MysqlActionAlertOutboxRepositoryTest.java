package io.github.actionguard.store.mysql;

import io.github.actionguard.core.model.ActionAlertOutbox;
import io.github.actionguard.core.model.ActionAlertOutboxStatus;
import io.github.actionguard.store.mysql.mapper.ActionAlertOutboxMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MysqlActionAlertOutboxRepositoryTest {

    private MysqlActionAlertOutboxRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:mysql_alert_outbox_" + UUID.randomUUID().toString().replace("-", "")
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/action-guard-mysql-schema.sql")).execute(dataSource);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new ClassPathResource("mapper/ActionAlertOutboxMapper.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        repository = new MysqlActionAlertOutboxRepository(new SqlSessionTemplate(factory).getMapper(ActionAlertOutboxMapper.class));
    }

    @Test
    void shouldPersistPayloadAndUpdateWithOptimisticLocking() {
        ActionAlertOutbox saved = repository.save(outbox("alert-1", "event-1", "dedupe-1", ActionAlertOutboxStatus.NEW, 0));

        ActionAlertOutbox loaded = repository.findById(saved.id()).orElseThrow();
        assertThat(loaded).isEqualTo(saved);

        ActionAlertOutbox updated = repository.save(withStatus(loaded, ActionAlertOutboxStatus.CLAIMED, 1));
        assertThat(updated.version()).isEqualTo(1);
        assertThat(repository.findById(saved.id())).get().satisfies(outbox -> {
            assertThat(outbox.status()).isEqualTo(ActionAlertOutboxStatus.CLAIMED);
            assertThat(outbox.deliveryAttemptCount()).isEqualTo(1);
            assertThat(outbox.detailsJson()).isEqualTo("{\"errorCode\":\"HTTP_500\"}");
        });

        assertThatThrownBy(() -> repository.save(withStatus(loaded, ActionAlertOutboxStatus.DONE, 0)))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void shouldEnforceEventAndDedupeUniqueness() {
        repository.save(outbox("alert-1", "event-1", "dedupe-1", ActionAlertOutboxStatus.NEW, 0));

        assertThatThrownBy(() -> repository.save(outbox("alert-2", "event-1", "dedupe-2", ActionAlertOutboxStatus.NEW, 0)))
                .isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> repository.save(outbox("alert-3", "event-3", "dedupe-1", ActionAlertOutboxStatus.NEW, 0)))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(repository.findByDedupeKey("dedupe-1")).isPresent();
    }

    @Test
    void shouldFindDueNewAndExpiredClaimedAlerts() {
        Instant now = Instant.parse("2026-09-12T08:00:00Z");
        repository.save(outbox("new-due", "event-1", "dedupe-1", ActionAlertOutboxStatus.NEW, 0));
        repository.save(new ActionAlertOutbox(
                "claimed-stale", "event-2", "dedupe-2", "RETRIES_EXHAUSTED", "HIGH", "retries exhausted", "failed",
                "order-flow", "action-1", "notify", "HTTP", now.minusSeconds(120), "{}", ActionAlertOutboxStatus.CLAIMED,
                now.minusSeconds(60), 0, null, 0, now.minusSeconds(120), now.minusSeconds(120)
        ));
        repository.save(new ActionAlertOutbox(
                "new-later", "event-3", "dedupe-3", "RETRIES_EXHAUSTED", "HIGH", "retries exhausted", "failed",
                "order-flow", "action-1", "notify", "HTTP", now, "{}", ActionAlertOutboxStatus.NEW,
                now.plusSeconds(60), 0, null, 0, now, now
        ));

        assertThat(repository.findRecoverable(now, now.minusSeconds(30), 10))
                .extracting(ActionAlertOutbox::id)
                .containsExactly("claimed-stale", "new-due");
    }

    private ActionAlertOutbox outbox(
            String id, String eventId, String dedupeKey, ActionAlertOutboxStatus status, int deliveryAttemptCount
    ) {
        Instant now = Instant.parse("2026-09-12T08:00:00Z");
        return new ActionAlertOutbox(
                id, eventId, dedupeKey, "RETRIES_EXHAUSTED", "HIGH", "retries exhausted", "failed",
                "order-flow", "action-1", "notify", "HTTP", now, "{\"errorCode\":\"HTTP_500\"}", status,
                now, deliveryAttemptCount, null, 0, now, now
        );
    }

    private ActionAlertOutbox withStatus(
            ActionAlertOutbox outbox, ActionAlertOutboxStatus status, int deliveryAttemptCount
    ) {
        return new ActionAlertOutbox(
                outbox.id(), outbox.eventId(), outbox.dedupeKey(), outbox.type(), outbox.level(), outbox.title(),
                outbox.message(), outbox.actionName(), outbox.actionInstanceId(), outbox.stepName(), outbox.stepType(),
                outbox.occurredAt(), outbox.detailsJson(), status, outbox.availableAt(), deliveryAttemptCount,
                outbox.lastErrorMessage(), outbox.version(), outbox.createdAt(), outbox.updatedAt().plusSeconds(1)
        );
    }
}
