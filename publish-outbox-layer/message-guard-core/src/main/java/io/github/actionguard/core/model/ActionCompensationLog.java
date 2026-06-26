package io.github.actionguard.core.model;

import java.time.Instant;

public record ActionCompensationLog(
        String id,
        String compensationBatchId,
        String actionInstanceId,
        String actionStepInstanceId,
        int stepIndex,
        String stepName,
        String stepType,
        String compensationStatus,
        String compensatorName,
        String resultMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
