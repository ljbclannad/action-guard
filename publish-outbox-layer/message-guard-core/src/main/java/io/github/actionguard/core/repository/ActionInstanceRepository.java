package io.github.actionguard.core.repository;

import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ActionInstanceRepository {

    Optional<ActionInstance> findById(String id);

    Optional<ActionInstance> findByActionNameAndBizKey(String actionName, String bizKey);

    default List<ActionInstance> findByStatusesAndUpdatedBefore(List<ActionStatus> statuses, Instant updatedBeforeOrAt, int limit) {
        return List.of();
    }

    ActionInstance save(ActionInstance instance);
}
