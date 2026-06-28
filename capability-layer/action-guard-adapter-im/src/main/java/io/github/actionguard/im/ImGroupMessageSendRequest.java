package io.github.actionguard.im;

import java.util.Map;

public record ImGroupMessageSendRequest(
        String actionName,
        String bizKey,
        String stepName,
        String target,
        String groupId,
        String messageType,
        String content,
        Map<String, Object> metadata
) {
}
