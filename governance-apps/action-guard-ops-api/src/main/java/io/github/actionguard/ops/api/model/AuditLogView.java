package io.github.actionguard.ops.api.model;

import java.time.Instant;

public record AuditLogView(
        String id,
        String actionInstanceId,
        String operationType,
        String operator,
        String requestPayloadJson,
        String resultStatus,
        String resultMessage,
        Instant createdAt
) {
}
