package io.github.actionguard.ops.api.model;

import java.time.Instant;

public record AuditLogQueryFilter(
        int page,
        int size,
        String actionInstanceId,
        String operationType,
        String operator,
        Instant createdFrom,
        Instant createdTo
) {
}
