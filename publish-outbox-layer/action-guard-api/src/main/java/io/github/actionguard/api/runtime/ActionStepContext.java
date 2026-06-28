package io.github.actionguard.api.runtime;

import java.util.Map;

public record ActionStepContext(
        String actionName,
        String bizKey,
        String stepName,
        String stepType,
        String target,
        Map<String, Object> attributes,
        Map<String, Object> payload
) {
}
