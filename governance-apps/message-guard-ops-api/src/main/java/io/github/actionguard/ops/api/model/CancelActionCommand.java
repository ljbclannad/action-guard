package io.github.actionguard.ops.api.model;

public record CancelActionCommand(
        String actionInstanceId,
        String operator
) {
}
