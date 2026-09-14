package io.github.actionguard.core.repository;

import io.github.actionguard.core.model.ActionAlertOutbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 告警可靠投递记录仓储。
 */
public interface ActionAlertOutboxRepository {

    ActionAlertOutbox save(ActionAlertOutbox outbox);

    Optional<ActionAlertOutbox> findByDedupeKey(String dedupeKey);

    default Optional<ActionAlertOutbox> findById(String id) {
        return Optional.empty();
    }

    default List<ActionAlertOutbox> findRecoverable(Instant availableBeforeOrAt, Instant claimedBeforeOrAt, int limit) {
        return List.of();
    }
}
