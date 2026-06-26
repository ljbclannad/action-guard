package io.github.actionguard.api.runtime;

import java.time.Instant;

public record ActionExecutionMessage(
        String messageId,
        String messageKey,
        String outboxId,
        String actionInstanceId,
        String topic,
        Instant createdAt
) {
}
