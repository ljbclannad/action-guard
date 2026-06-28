package io.github.actionguard.ops.api.repository.jdbc;

import io.github.actionguard.ops.api.model.ActionOpsAuditLog;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcActionAuditLogRepositoryTest {

    @Test
    void shouldInsertAndQueryAuditLogRows() {
        ActionOpsAuditLog log = new ActionOpsAuditLog(
                "audit-1",
                "act-1",
                "RETRY",
                "anonymous",
                "{\"reason\":\"manual retry\"}",
                "SUCCESS",
                "ok",
                Instant.parse("2026-06-26T12:00:00Z")
        );

        JdbcActionAuditLogRepository repository = TestAuditLogRepositoryFactory.create();
        repository.save(log);

        assertThat(repository.findByActionInstanceId("act-1")).hasSize(1);
        assertThat(repository.findByActionInstanceId("act-1").get(0).operationType()).isEqualTo("RETRY");
    }
}
