package io.github.actionguard.ops.api.service;

import io.github.actionguard.ops.api.model.AuditLogQueryFilter;
import io.github.actionguard.ops.api.repository.ActionAuditLogRepository;
import io.github.actionguard.ops.api.repository.jdbc.InMemoryAuditLogRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActionAuditServiceTest {

    @Test
    void shouldPersistAndQueryAuditLogs() {
        ActionAuditLogRepository repository = InMemoryAuditLogRepository.create();
        ActionAuditService service = new ActionAuditService(repository);

        service.record("act-1", "RETRY", "anonymous", "{\"reason\":\"manual retry\"}", "SUCCESS", "ok");

        AuditLogQueryFilter filter = new AuditLogQueryFilter(1, 20, "act-1", null, null, null, null);
        assertThat(service.query(filter).items()).hasSize(1);
        assertThat(service.query(filter).items().get(0).operationType()).isEqualTo("RETRY");
    }
}
