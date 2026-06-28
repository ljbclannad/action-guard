package io.github.actionguard.ops.api.model;

import io.github.actionguard.core.model.ActionStatus;

import java.time.Instant;

public record ActionListItem(
        String actionInstanceId,
        String actionName,
        String bizKey,
        ActionStatus status,
        int currentStepIndex,
        int totalStepCount,
        String lastErrorCode,
        String lastErrorMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
