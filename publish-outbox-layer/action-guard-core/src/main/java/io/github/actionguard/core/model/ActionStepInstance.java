package io.github.actionguard.core.model;

import java.time.Instant;
import java.util.Map;

public record ActionStepInstance(
        String id,
        String actionInstanceId,
        int stepIndex,
        String stepName,
        String stepType,
        String target,
        ActionStepStatus status,
        int attemptCount,
        Map<String, Object> payload,
        String lastErrorCode,
        String lastErrorMessage,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
}
