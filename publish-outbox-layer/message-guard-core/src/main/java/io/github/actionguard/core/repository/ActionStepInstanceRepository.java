package io.github.actionguard.core.repository;

import io.github.actionguard.core.model.ActionStepInstance;

import java.util.List;

public interface ActionStepInstanceRepository {

    ActionStepInstance save(ActionStepInstance stepInstance);

    List<ActionStepInstance> saveAll(List<ActionStepInstance> stepInstances);

    List<ActionStepInstance> findByActionInstanceId(String actionInstanceId);
}
