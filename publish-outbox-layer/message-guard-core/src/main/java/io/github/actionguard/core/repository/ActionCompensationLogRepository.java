package io.github.actionguard.core.repository;

import io.github.actionguard.core.model.ActionCompensationLog;

import java.util.List;

public interface ActionCompensationLogRepository {

    ActionCompensationLog save(ActionCompensationLog log);

    List<ActionCompensationLog> findByActionInstanceId(String actionInstanceId);
}
