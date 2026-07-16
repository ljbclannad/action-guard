package io.github.actionguard.core.repository;

import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionStatus;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryActionInstanceRepository implements ActionInstanceRepository {

    private final Map<String, ActionInstance> storageByBusinessKey = new ConcurrentHashMap<>();
    private final Map<String, ActionInstance> storageById = new ConcurrentHashMap<>();

    @Override
    public Optional<ActionInstance> findById(String id) {
        return Optional.ofNullable(storageById.get(id));
    }

    @Override
    public Optional<ActionInstance> findByActionNameAndBizKey(String actionName, String bizKey) {
        return Optional.ofNullable(storageByBusinessKey.get(key(actionName, bizKey)));
    }

    @Override
    public Optional<ActionInstance> findByIdempotencyKey(String idempotencyKey) {
        return storageById.values().stream().filter(instance -> instance.idempotencyKey().equals(idempotencyKey)).findFirst();
    }

    @Override
    public List<ActionInstance> findByStatusesAndUpdatedBefore(List<ActionStatus> statuses, Instant updatedBeforeOrAt, int limit) {
        if (statuses == null || statuses.isEmpty() || limit <= 0) {
            return List.of();
        }
        return storageById.values().stream()
                .filter(instance -> statuses.contains(instance.status()) && !instance.updatedAt().isAfter(updatedBeforeOrAt))
                .sorted(Comparator.comparing(ActionInstance::updatedAt).thenComparing(ActionInstance::createdAt))
                .limit(limit)
                .toList();
    }

    @Override
    public ActionInstance save(ActionInstance instance) {
        ActionInstance persisted = storageById.compute(instance.id(), (id, existing) -> {
            if (existing == null) {
                return instance;
            }
            if (existing.version() != instance.version()) {
                throw new OptimisticLockingFailureException("ActionInstance version conflict: " + instance.id());
            }
            return withNextVersion(instance, existing.version());
        });
        storageByBusinessKey.put(key(persisted.actionName(), persisted.bizKey()), persisted);
        return persisted;
    }

    private ActionInstance withNextVersion(ActionInstance instance, int currentVersion) {
        return new ActionInstance(
                instance.id(),
                instance.actionName(),
                instance.definitionVersion(),
                instance.bizKey(),
                instance.idempotencyKey(),
                instance.status(),
                instance.currentStepIndex(),
                instance.totalStepCount(),
                instance.attributes(),
                instance.lastErrorCode(),
                instance.lastErrorMessage(),
                currentVersion + 1,
                instance.createdAt(),
                instance.updatedAt()
        );
    }

    private String key(String actionName, String bizKey) {
        return actionName + ":" + bizKey;
    }
}
