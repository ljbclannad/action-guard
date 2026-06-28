package io.github.actionguard.ops.api.repository;

import io.github.actionguard.ops.api.model.CompensationLogView;

import java.util.List;

public interface ActionCompensationLogQueryRepository {

    List<CompensationLogView> findByActionInstanceId(String actionInstanceId);
}
