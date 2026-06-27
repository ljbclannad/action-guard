package io.github.actionguard.core.repository;

import io.github.actionguard.core.model.ActionGovernancePolicy;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryActionGovernancePolicyRepository implements ActionGovernancePolicyRepository {

    private final Map<String, ActionGovernancePolicy> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<ActionGovernancePolicy> findByActionName(String actionName) {
        return Optional.ofNullable(storage.get(actionName));
    }

    @Override
    public ActionGovernancePolicy save(ActionGovernancePolicy policy) {
        storage.put(policy.actionName(), policy);
        return policy;
    }
}
