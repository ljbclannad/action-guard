package io.github.actionguard.ops.api.repository;

import io.github.actionguard.ops.api.model.ActionOutboxView;

import java.util.List;

/** 只读查询 Action 关联的 Outbox 投递快照。 */
public interface ActionOutboxQueryRepository {

    List<ActionOutboxView> findByActionInstanceId(String actionInstanceId);
}
