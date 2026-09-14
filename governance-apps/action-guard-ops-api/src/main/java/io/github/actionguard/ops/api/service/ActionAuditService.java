package io.github.actionguard.ops.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    public ActionAuditService(ActionAuditLogRepository repository) {
        this(repository, new ObjectMapper());
    }

    public ActionAuditService(ActionAuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
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

    public void recordCommand(
            String actionInstanceId,
            String operationType,
            String operator,
            String reason,
            String resultStatus,
            String resultMessage
    ) {
        record(actionInstanceId, operationType, operator, toJson(java.util.Map.of("reason", reason)), resultStatus, resultMessage);
    }

    public void recordTransition(
            String actionInstanceId,
            String operationType,
            String operator,
            String reason,
            ActionTransitionResult transitionResult,
            String resultStatus,
            String resultMessage
    ) {
        record(
                actionInstanceId,
                operationType,
                operator,
                toJson(java.util.Map.of(
                        "reason", reason,
                        "event", transitionResult.event().name(),
                        "fromStatus", transitionResult.fromStatus().name(),
                        "toStatus", transitionResult.toStatus().name()
                )),
                resultStatus,
                resultMessage
        );
    }

    private String toJson(java.util.Map<String, String> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法序列化治理审计请求", ex);
        }
    }

    public PageResult<AuditLogView> query(AuditLogQueryFilter filter) {
        return repository.query(filter);
    }
}
