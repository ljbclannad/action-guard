package io.github.actionguard.ops.api.model;

import java.time.Instant;

public record ActionQueryFilter(
        int page,
        int size,
        String actionName,
        String bizKey,
        String status,
        Instant createdFrom,
        Instant createdTo
) {
}
