package io.github.actionguard.core.model;

import java.time.Instant;

public record ActionOutbox(
        String id,
        String actionInstanceId,
        String topic,
        ActionOutboxStatus status,
        Instant availableAt,
        int attemptCount,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
}
