package io.github.actionguard.ops.api.model;

import io.github.actionguard.core.model.ActionConsumeStatus;

import java.time.Instant;

public record ConsumeDetailView(
        String messageId,
        String consumerGroup,
        ActionConsumeStatus consumeStatus,
        int attemptCount,
        String lastErrorMessage,
        Instant firstReceivedAt,
        Instant lastReceivedAt,
        Instant updatedAt
) {
}
