package io.github.actionguard.api.runtime;

import java.time.Instant;
import java.util.Map;

public record ActionAlertEvent(
        ActionAlertType type,
        ActionAlertLevel level,
        String title,
        String message,
        String actionName,
        String actionInstanceId,
        String stepName,
        String stepType,
        Instant occurredAt,
        Map<String, String> details
) {
}
