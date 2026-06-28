package io.github.actionguard.ops.api.model;

public record CompensateActionCommand(
        String actionInstanceId,
        String operator
) {
}
