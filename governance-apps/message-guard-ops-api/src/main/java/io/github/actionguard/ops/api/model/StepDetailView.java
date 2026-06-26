package io.github.actionguard.ops.api.model;

import io.github.actionguard.core.model.ActionStepStatus;

import java.time.Instant;

public record StepDetailView(
        int stepIndex,
        String stepName,
        String stepType,
        String target,
        ActionStepStatus status,
        int attemptCount,
        String lastErrorCode,
        String lastErrorMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
