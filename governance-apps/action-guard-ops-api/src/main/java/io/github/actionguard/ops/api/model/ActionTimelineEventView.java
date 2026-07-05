package io.github.actionguard.ops.api.model;

import java.time.Instant;

public record ActionTimelineEventView(
        Instant occurredAt,
        String category,
        String title,
        String summary,
        String fromStatus,
        String toStatus,
        String stepName,
        String stepType
) {
}
