package io.github.actionguard.core.repository;

import io.github.actionguard.core.model.ActionGovernancePolicy;

import java.util.Optional;

public interface ActionGovernancePolicyRepository {

    Optional<ActionGovernancePolicy> findByActionName(String actionName);

    ActionGovernancePolicy save(ActionGovernancePolicy policy);
}
