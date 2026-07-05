package io.github.actionguard.core.repository;

import io.github.actionguard.core.model.ActionTransitionLog;

import java.util.List;

public interface ActionTransitionLogRepository {

    ActionTransitionLog save(ActionTransitionLog log);

    List<ActionTransitionLog> findByActionInstanceId(String actionInstanceId);
}
