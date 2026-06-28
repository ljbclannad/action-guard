package io.github.actionguard.notify;

import java.util.List;
import java.util.Map;

public record NotifyInAppRequest(
        String actionName,
        String bizKey,
        String stepName,
        String target,
        List<String> receiverIds,
        String templateId,
        Map<String, Object> variables
) {
}
