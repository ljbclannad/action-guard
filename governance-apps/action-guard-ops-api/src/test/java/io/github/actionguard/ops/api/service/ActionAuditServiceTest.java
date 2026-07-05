package io.github.actionguard.ops.api.service;

import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.runtime.state.ActionTransitionEvent;
import io.github.actionguard.core.runtime.state.ActionTransitionResult;
import io.github.actionguard.ops.api.model.AuditLogQueryFilter;
import io.github.actionguard.ops.api.repository.ActionAuditLogRepository;
import io.github.actionguard.ops.api.repository.jdbc.InMemoryAuditLogRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

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

    @Test
    void shouldPersistTransitionPayloadForAuditLog() {
        ActionAuditLogRepository repository = InMemoryAuditLogRepository.create();
        ActionAuditService service = new ActionAuditService(repository);
        ActionInstance transitioned = new ActionInstance(
                "act-1",
                "order-cancel-flow",
                "order:1",
                ActionStatus.IGNORED,
                0,
                1,
                Map.of(),
                null,
                null,
                0,
                Instant.parse("2026-06-26T12:00:00Z"),
                Instant.parse("2026-06-26T12:01:00Z")
        );

        service.recordTransition(
                "act-1",
                "CANCEL",
                "anonymous",
                new ActionTransitionResult(
                        ActionStatus.DISPATCHING,
                        ActionStatus.IGNORED,
                        ActionTransitionEvent.MANUAL_CANCEL_REQUESTED,
                        transitioned
                ),
                "SUCCESS",
                "action ignored"
        );

        AuditLogQueryFilter filter = new AuditLogQueryFilter(1, 20, "act-1", null, null, null, null);
        assertThat(service.query(filter).items()).hasSize(1);
        assertThat(service.query(filter).items().get(0).requestPayloadJson())
                .contains("MANUAL_CANCEL_REQUESTED")
                .contains("DISPATCHING")
                .contains("IGNORED");
    }
}
