package io.github.actionguard.ops.api.repository.jdbc;

import io.github.actionguard.ops.api.model.ActionOpsAuditLog;
import io.github.actionguard.ops.api.model.AuditLogQueryFilter;
import io.github.actionguard.ops.api.model.AuditLogView;
import io.github.actionguard.ops.api.model.PageResult;
import io.github.actionguard.ops.api.repository.ActionAuditLogRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class InMemoryAuditLogRepository implements ActionAuditLogRepository {

    private final List<ActionOpsAuditLog> storage = new ArrayList<>();

    public static InMemoryAuditLogRepository create() {
        return new InMemoryAuditLogRepository();
    }

    @Override
    public ActionOpsAuditLog save(ActionOpsAuditLog log) {
        storage.add(log);
        return log;
    }

    @Override
    public List<ActionOpsAuditLog> findByActionInstanceId(String actionInstanceId) {
        return storage.stream()
                .filter(log -> log.actionInstanceId().equals(actionInstanceId))
                .toList();
    }

    @Override
    public PageResult<AuditLogView> query(AuditLogQueryFilter filter) {
        List<AuditLogView> filtered = storage.stream()
                .filter(log -> filter.actionInstanceId() == null || filter.actionInstanceId().equals(log.actionInstanceId()))
                .filter(log -> filter.operationType() == null || filter.operationType().equals(log.operationType()))
                .filter(log -> filter.operator() == null || filter.operator().equals(log.operator()))
                .filter(log -> filter.createdFrom() == null || !log.createdAt().isBefore(filter.createdFrom()))
                .filter(log -> filter.createdTo() == null || !log.createdAt().isAfter(filter.createdTo()))
                .sorted(Comparator.comparing(ActionOpsAuditLog::createdAt).reversed())
                .map(this::toView)
                .toList();
        int page = Math.max(1, filter.page());
        int size = Math.max(1, filter.size());
        int fromIndex = Math.min((page - 1) * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        return new PageResult<>(filtered.subList(fromIndex, toIndex), filtered.size(), page, size);
    }

    private AuditLogView toView(ActionOpsAuditLog log) {
        return new AuditLogView(
                log.id(),
                log.actionInstanceId(),
                log.operationType(),
                log.operator(),
                log.requestPayloadJson(),
                log.resultStatus(),
                log.resultMessage(),
                log.createdAt()
        );
    }
}
