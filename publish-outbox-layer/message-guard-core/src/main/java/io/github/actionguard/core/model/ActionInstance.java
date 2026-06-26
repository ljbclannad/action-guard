package io.github.actionguard.core.model;

import java.time.Instant;

public record ActionInstance(
        String actionName,
        String bizKey,
        ActionStatus status,
        int currentStepIndex,
        Instant updatedAt
) {
}
