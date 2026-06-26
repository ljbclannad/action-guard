package io.github.actionguard.ops.api.model;

public record SkipStepCommand(
        String actionInstanceId,
        String operator
) {
}
