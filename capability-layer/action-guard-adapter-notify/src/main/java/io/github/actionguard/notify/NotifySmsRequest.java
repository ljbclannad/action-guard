package io.github.actionguard.notify;

import java.util.List;
import java.util.Map;

public record NotifySmsRequest(
        String actionName,
        String bizKey,
        String stepName,
        String target,
        List<String> phoneNumbers,
        String sign,
        String templateId,
        Map<String, Object> variables
) {
}
