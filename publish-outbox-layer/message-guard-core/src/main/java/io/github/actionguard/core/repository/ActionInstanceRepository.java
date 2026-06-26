package io.github.actionguard.core.repository;

import io.github.actionguard.core.model.ActionInstance;

import java.util.Optional;

public interface ActionInstanceRepository {

    Optional<ActionInstance> findById(String id);

    Optional<ActionInstance> findByActionNameAndBizKey(String actionName, String bizKey);

    ActionInstance save(ActionInstance instance);
}
