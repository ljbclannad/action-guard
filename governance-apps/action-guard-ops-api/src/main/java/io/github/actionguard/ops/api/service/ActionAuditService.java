package io.github.actionguard.ops.api.service;

import io.github.actionguard.core.runtime.state.ActionTransitionResult;
import io.github.actionguard.ops.api.model.ActionOpsAuditLog;
import io.github.actionguard.ops.api.model.AuditLogQueryFilter;
import io.github.actionguard.ops.api.model.AuditLogView;
import io.github.actionguard.ops.api.model.PageResult;
import io.github.actionguard.ops.api.repository.ActionAuditLogRepository;

import java.time.Instant;
import java.util.UUID;

public class ActionAuditService {

    private final ActionAuditLogRepository repository;

    public ActionAuditService(ActionAuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(
            String actionInstanceId,
            String operationType,
            String operator,
            String requestPayloadJson,
            String resultStatus,
            String resultMessage
    ) {
        repository.save(new ActionOpsAuditLog(
                UUID.randomUUID().toString(),
                actionInstanceId,
                operationType,
                operator == null || operator.isBlank() ? "anonymous" : operator,
                requestPayloadJson,
                resultStatus,
                resultMessage,
                Instant.now()
        ));
    }

    public void recordTransition(
            String actionInstanceId,
            String operationType,
            String operator,
            ActionTransitionResult transitionResult,
            String resultStatus,
            String resultMessage
    ) {
        record(
                actionInstanceId,
                operationType,
                operator,
                "{\"event\":\"" + transitionResult.event().name()
                        + "\",\"fromStatus\":\"" + transitionResult.fromStatus().name()
                        + "\",\"toStatus\":\"" + transitionResult.toStatus().name() + "\"}",
                resultStatus,
                resultMessage
        );
    }

    public PageResult<AuditLogView> query(AuditLogQueryFilter filter) {
        return repository.query(filter);
    }
}
