package io.github.actionguard.api.runtime;

public record ActionCompensationContext(
        String actionName,
        String bizKey,
        String stepName,
        Object payload
) {
}
