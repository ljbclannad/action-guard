package io.github.actionguard.core.repository;

import io.github.actionguard.core.model.ActionInstance;

import java.util.ConcurrentModificationException;
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
    public ActionInstance save(ActionInstance instance) {
        ActionInstance existing = storageById.get(instance.id());
        ActionInstance persisted = existing == null
                ? instance
                : withNextVersion(instance, existing.version());
        if (existing != null && existing.version() != instance.version()) {
            throw new ConcurrentModificationException("ActionInstance version conflict: " + instance.id());
        }
        storageByBusinessKey.put(key(persisted.actionName(), persisted.bizKey()), persisted);
        storageById.put(persisted.id(), persisted);
        return persisted;
    }

    private ActionInstance withNextVersion(ActionInstance instance, int currentVersion) {
        return new ActionInstance(
                instance.id(),
                instance.actionName(),
                instance.bizKey(),
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
