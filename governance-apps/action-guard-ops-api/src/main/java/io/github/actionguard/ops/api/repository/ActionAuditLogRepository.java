package io.github.actionguard.ops.api.repository;

import io.github.actionguard.ops.api.model.ActionOpsAuditLog;
import io.github.actionguard.ops.api.model.AuditLogQueryFilter;
import io.github.actionguard.ops.api.model.AuditLogView;
import io.github.actionguard.ops.api.model.PageResult;

import java.util.List;

public interface ActionAuditLogRepository {

    ActionOpsAuditLog save(ActionOpsAuditLog log);

    List<ActionOpsAuditLog> findByActionInstanceId(String actionInstanceId);

    PageResult<AuditLogView> query(AuditLogQueryFilter filter);
}
