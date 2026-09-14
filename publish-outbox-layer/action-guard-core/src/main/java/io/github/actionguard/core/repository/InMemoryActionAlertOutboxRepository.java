package io.github.actionguard.core.repository;

import io.github.actionguard.core.model.ActionAlertOutbox;
import io.github.actionguard.core.model.ActionAlertOutboxStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 供最小接入和测试使用的内存告警 Outbox 仓储。
 */
public class InMemoryActionAlertOutboxRepository implements ActionAlertOutboxRepository {

    private final Map<String, ActionAlertOutbox> storage = new ConcurrentHashMap<>();
    private final Map<String, String> idsByDedupeKey = new ConcurrentHashMap<>();

    @Override
    public ActionAlertOutbox save(ActionAlertOutbox outbox) {
        if (storage.putIfAbsent(outbox.id(), outbox) == null) {
            String existingId = idsByDedupeKey.putIfAbsent(outbox.dedupeKey(), outbox.id());
            if (existingId != null) {
                storage.remove(outbox.id(), outbox);
                throw new DuplicateKeyException("ActionAlertOutbox dedupe key conflict: " + outbox.dedupeKey());
            }
            return outbox;
        }
        return storage.compute(outbox.id(), (id, existing) -> {
            if (existing.version() != outbox.version()) {
                throw new OptimisticLockingFailureException("ActionAlertOutbox version conflict: " + outbox.id());
            }
            return withNextVersion(outbox, existing.version());
        });
    }

    @Override
    public Optional<ActionAlertOutbox> findByDedupeKey(String dedupeKey) {
        return Optional.ofNullable(idsByDedupeKey.get(dedupeKey)).map(storage::get);
    }

    @Override
    public Optional<ActionAlertOutbox> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<ActionAlertOutbox> findRecoverable(Instant availableBeforeOrAt, Instant claimedBeforeOrAt, int limit) {
        return storage.values().stream()
                .filter(outbox -> (outbox.status() == ActionAlertOutboxStatus.NEW
                        && !outbox.availableAt().isAfter(availableBeforeOrAt))
                        || (outbox.status() == ActionAlertOutboxStatus.CLAIMED
                        && !outbox.updatedAt().isAfter(claimedBeforeOrAt)))
                .sorted(Comparator.comparing(ActionAlertOutbox::availableAt).thenComparing(ActionAlertOutbox::createdAt))
                .limit(Math.max(0, limit))
                .toList();
    }

    private ActionAlertOutbox withNextVersion(ActionAlertOutbox outbox, int currentVersion) {
        return new ActionAlertOutbox(
                outbox.id(), outbox.eventId(), outbox.dedupeKey(), outbox.type(), outbox.level(), outbox.title(),
                outbox.message(), outbox.actionName(), outbox.actionInstanceId(), outbox.stepName(), outbox.stepType(),
                outbox.occurredAt(), outbox.detailsJson(), outbox.status(), outbox.availableAt(),
                outbox.deliveryAttemptCount(), outbox.lastErrorMessage(), currentVersion + 1,
                outbox.createdAt(), outbox.updatedAt()
        );
    }
}
