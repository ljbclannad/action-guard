package io.github.actionguard.ops.api.model;

public record RetryActionCommand(
        String actionInstanceId,
        String operator
) {
}
