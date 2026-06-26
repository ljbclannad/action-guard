package io.github.actionguard.core.repository;

import io.github.actionguard.core.model.ActionStepInstance;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryActionStepInstanceRepository implements ActionStepInstanceRepository {

    private final Map<String, ActionStepInstance> storageById = new ConcurrentHashMap<>();

    @Override
    public ActionStepInstance save(ActionStepInstance stepInstance) {
        ActionStepInstance existing = storageById.get(stepInstance.id());
        ActionStepInstance persisted = existing == null
                ? stepInstance
                : withNextVersion(stepInstance, existing.version());
        if (existing != null && existing.version() != stepInstance.version()) {
            throw new OptimisticLockingFailureException("ActionStepInstance version conflict: " + stepInstance.id());
        }
        storageById.put(persisted.id(), persisted);
        return persisted;
    }

    @Override
    public List<ActionStepInstance> saveAll(List<ActionStepInstance> stepInstances) {
        stepInstances.forEach(this::save);
        return List.copyOf(stepInstances);
    }

    @Override
    public List<ActionStepInstance> findByActionInstanceId(String actionInstanceId) {
        return storageById.values().stream()
                .filter(step -> step.actionInstanceId().equals(actionInstanceId))
                .sorted(java.util.Comparator.comparingInt(ActionStepInstance::stepIndex))
                .toList();
    }

    private ActionStepInstance withNextVersion(ActionStepInstance stepInstance, int currentVersion) {
        return new ActionStepInstance(
                stepInstance.id(),
                stepInstance.actionInstanceId(),
                stepInstance.stepIndex(),
                stepInstance.stepName(),
                stepInstance.stepType(),
                stepInstance.target(),
                stepInstance.status(),
                stepInstance.attemptCount(),
                stepInstance.payload(),
                stepInstance.lastErrorCode(),
                stepInstance.lastErrorMessage(),
                currentVersion + 1,
                stepInstance.createdAt(),
                stepInstance.updatedAt()
        );
    }
}
