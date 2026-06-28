package io.github.actionguard.ops.api.model;

import java.util.List;

public record PageResult<T>(
        List<T> items,
        long total,
        int page,
        int size
) {
}
