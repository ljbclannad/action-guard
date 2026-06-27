package io.github.actionguard.im;

import java.util.List;
import java.util.Map;

public record ImGroupCreateRequest(
        String actionName,
        String bizKey,
        String stepName,
        String target,
        String groupName,
        String owner,
        List<String> members,
        String avatar,
        Map<String, Object> metadata
) {
}
