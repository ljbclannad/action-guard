package io.github.actionguard.api;

import java.util.List;
import java.util.Map;

public record ActionRequest(
        String actionName,
        String bizKey,
        Map<String, Object> attributes,
        List<ActionStepRequest> steps,
        String idempotencyKey
) {

    public ActionRequest(String actionName, String bizKey, Map<String, Object> attributes, List<ActionStepRequest> steps) {
        this(actionName, bizKey, attributes, steps, null);
    }
}
