package io.github.actionguard.core.repository;

import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryActionOutboxRepository implements ActionOutboxRepository {

    private final Map<String, ActionOutbox> storage = new ConcurrentHashMap<>();

    @Override
    public ActionOutbox save(ActionOutbox outbox) {
        return storage.compute(outbox.id(), (id, existing) -> {
            if (existing == null) {
                return outbox;
            }
            if (existing.version() != outbox.version()) {
                throw new OptimisticLockingFailureException("ActionOutbox version conflict: " + outbox.id());
            }
            return withNextVersion(outbox, existing.version());
        });
    }

    @Override
    public Optional<ActionOutbox> findByActionInstanceId(String actionInstanceId) {
        return storage.values().stream()
                .filter(outbox -> outbox.actionInstanceId().equals(actionInstanceId))
                .findFirst();
    }

    @Override
    public Optional<ActionOutbox> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<ActionOutbox> findRecoverable(Instant availableBeforeOrAt, Instant claimedBeforeOrAt, int limit) {
        return storage.values().stream()
                .filter(outbox -> outbox.status() == ActionOutboxStatus.NEW && !outbox.availableAt().isAfter(availableBeforeOrAt)
                        || outbox.status() == ActionOutboxStatus.CLAIMED && !outbox.updatedAt().isAfter(claimedBeforeOrAt))
                .sorted(Comparator.comparing(ActionOutbox::availableAt).thenComparing(ActionOutbox::createdAt))
                .limit(Math.max(0, limit))
                .toList();
    }

    private ActionOutbox withNextVersion(ActionOutbox outbox, int currentVersion) {
        return new ActionOutbox(
                outbox.id(),
                outbox.actionInstanceId(),
                outbox.topic(),
                outbox.dispatchId(),
                outbox.status(),
                outbox.availableAt(),
                outbox.attemptCount(),
                currentVersion + 1,
                outbox.createdAt(),
                outbox.updatedAt()
        );
    }
}
