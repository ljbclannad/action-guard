package io.github.actionguard.notify;

import java.util.List;
import java.util.Map;

public record NotifyEmailRequest(
        String actionName,
        String bizKey,
        String stepName,
        String target,
        List<String> recipients,
        String subject,
        String body,
        String templateId,
        Map<String, Object> variables
) {
}
