package io.github.actionguard.core.model;

import java.time.Instant;

public record ActionGovernancePolicy(
        String id,
        String actionName,
        Boolean compensationEnabled,
        String retryPolicyJson,
        String alertPolicyJson,
        Instant updatedAt
) {
}
