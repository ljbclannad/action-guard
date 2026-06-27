package io.github.actionguard.im;

import java.util.List;

public record ImGroupInviteRequest(
        String actionName,
        String bizKey,
        String stepName,
        String target,
        String groupId,
        String inviter,
        List<String> members
) {
}
