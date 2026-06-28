package io.github.actionguard.core.repository;

import io.github.actionguard.core.model.ActionOutbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ActionOutboxRepository {

    ActionOutbox save(ActionOutbox outbox);

    Optional<ActionOutbox> findByActionInstanceId(String actionInstanceId);

    default Optional<ActionOutbox> findById(String id) {
        return Optional.empty();
    }

    default List<ActionOutbox> findRecoverable(Instant availableBeforeOrAt, Instant claimedBeforeOrAt, int limit) {
        return List.of();
    }
}
