package io.github.actionguard.core.model;

import java.time.Instant;

public record ActionConsumeLog(
        String id,
        String messageId,
        String actionInstanceId,
        String consumerGroup,
        ActionConsumeStatus consumeStatus,
        String dedupeKey,
        int attemptCount,
        String lastErrorMessage,
        int version,
        Instant firstReceivedAt,
        Instant lastReceivedAt,
        Instant updatedAt
) {
}
