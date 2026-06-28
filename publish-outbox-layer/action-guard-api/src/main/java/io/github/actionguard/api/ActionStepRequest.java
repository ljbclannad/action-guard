package io.github.actionguard.api;

import java.util.Map;

public record ActionStepRequest(
        String stepName,
        String stepType,
        String target,
        Map<String, Object> payload
) {
}
