package io.github.actionguard.ops.api.repository;

import io.github.actionguard.ops.api.model.ActionAlertOutboxView;

import java.util.List;

/**
 * 只读查询 Action 关联的可靠告警投递诊断。
 */
public interface ActionAlertOutboxQueryRepository {

    List<ActionAlertOutboxView> findByActionInstanceId(String actionInstanceId);
}
