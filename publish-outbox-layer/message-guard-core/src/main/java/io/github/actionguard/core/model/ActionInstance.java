package io.github.actionguard.core.model;

import java.time.Instant;
import java.util.Map;

public record ActionInstance(
        String id,
        String actionName,
        String bizKey,
        ActionStatus status,
        int currentStepIndex,
        int totalStepCount,
        Map<String, Object> attributes,
        String lastErrorCode,
        String lastErrorMessage,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
}
