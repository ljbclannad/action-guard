package io.github.actionguard.ops.api.security;

import java.util.Set;

public record ActionOpsPrincipal(String operatorId, Set<ActionOpsPermission> permissions) {

    public ActionOpsPrincipal {
        if (operatorId == null || operatorId.isBlank()) {
            throw new IllegalArgumentException("operatorId 不能为空");
        }
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public boolean hasPermission(ActionOpsPermission permission) {
        return permissions.contains(permission);
    }
}
