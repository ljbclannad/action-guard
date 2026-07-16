package io.github.actionguard.core.model;

import java.time.Instant;

public record ActionOutbox(
        String id,
        String actionInstanceId,
        String topic,
        String dispatchId,
        ActionOutboxStatus status,
        Instant availableAt,
        int attemptCount,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
    public ActionOutbox(
            String id, String actionInstanceId, String topic, ActionOutboxStatus status,
            Instant availableAt, int attemptCount, int version, Instant createdAt, Instant updatedAt
    ) {
        this(id, actionInstanceId, topic, id, status, availableAt, attemptCount, version, createdAt, updatedAt);
    }
}
