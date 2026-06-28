package io.github.actionguard.api.runtime;

public record ActionCompensationContext(
        String actionName,
        String bizKey,
        String stepName,
        String stepType,
        Object payload
) {
}
