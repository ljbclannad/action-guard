package io.github.actionguard.ops.api.model;

import java.time.Instant;

public record CompensationLogView(
        String compensationBatchId,
        int stepIndex,
        String stepName,
        String stepType,
        String compensationStatus,
        String compensatorName,
        String resultMessage,
        Instant createdAt
) {
}
