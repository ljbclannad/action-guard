package io.github.actionguard.core.repository;

import io.github.actionguard.core.model.ActionOutbox;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryActionOutboxRepository implements ActionOutboxRepository {

    private final Map<String, ActionOutbox> storage = new ConcurrentHashMap<>();

    @Override
    public ActionOutbox save(ActionOutbox outbox) {
        ActionOutbox existing = storage.get(outbox.id());
        ActionOutbox persisted = existing == null ? outbox : withNextVersion(outbox, existing.version());
        if (existing != null && existing.version() != outbox.version()) {
            throw new OptimisticLockingFailureException("ActionOutbox version conflict: " + outbox.id());
        }
        storage.put(persisted.id(), persisted);
        return persisted;
    }

    @Override
    public Optional<ActionOutbox> findByActionInstanceId(String actionInstanceId) {
        return storage.values().stream()
                .filter(outbox -> outbox.actionInstanceId().equals(actionInstanceId))
                .findFirst();
    }

    private ActionOutbox withNextVersion(ActionOutbox outbox, int currentVersion) {
        return new ActionOutbox(
                outbox.id(),
                outbox.actionInstanceId(),
                outbox.topic(),
                outbox.status(),
                outbox.availableAt(),
                outbox.attemptCount(),
                currentVersion + 1,
                outbox.createdAt(),
                outbox.updatedAt()
        );
    }
}
