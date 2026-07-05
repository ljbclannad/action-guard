package io.github.actionguard.ops.api.model;

import io.github.actionguard.core.model.ActionStatus;

import java.time.Instant;
import java.util.List;

public record ActionDetailView(
        String actionInstanceId,
        String actionName,
        String bizKey,
        ActionStatus status,
        int currentStepIndex,
        int totalStepCount,
        String lastErrorCode,
        String lastErrorMessage,
        Instant createdAt,
        Instant updatedAt,
        List<StepDetailView> steps,
        List<ConsumeDetailView> consumes,
        List<ActionTimelineEventView> timeline
) {
}
