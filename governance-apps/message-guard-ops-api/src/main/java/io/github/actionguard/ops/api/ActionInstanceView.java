package io.github.actionguard.ops.api;

import io.github.actionguard.core.model.ActionStatus;

public record ActionInstanceView(
        String actionName,
        String bizKey,
        ActionStatus status,
        int totalSteps
) {
}
