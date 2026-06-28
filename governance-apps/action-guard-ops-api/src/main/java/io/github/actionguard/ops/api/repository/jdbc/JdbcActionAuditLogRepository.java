package io.github.actionguard.ops.api.repository.jdbc;

import io.github.actionguard.ops.api.model.ActionOpsAuditLog;
import io.github.actionguard.ops.api.model.AuditLogQueryFilter;
import io.github.actionguard.ops.api.model.AuditLogView;
import io.github.actionguard.ops.api.model.PageResult;
import io.github.actionguard.ops.api.repository.ActionAuditLogRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class JdbcActionAuditLogRepository implements ActionAuditLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcActionAuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ActionOpsAuditLog save(ActionOpsAuditLog log) {
        jdbcTemplate.update(
                "insert into action_ops_audit_log (id, action_instance_id, operation_type, operator, request_payload_json, result_status, result_message, created_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
                log.id(),
                log.actionInstanceId(),
                log.operationType(),
                log.operator(),
                log.requestPayloadJson(),
                log.resultStatus(),
                log.resultMessage(),
                Timestamp.from(log.createdAt())
        );
        return log;
    }

    @Override
    public List<ActionOpsAuditLog> findByActionInstanceId(String actionInstanceId) {
        return jdbcTemplate.query(
                "select id, action_instance_id, operation_type, operator, request_payload_json, result_status, result_message, created_at from action_ops_audit_log where action_instance_id = ? order by created_at desc",
                (rs, rowNum) -> new ActionOpsAuditLog(
                        rs.getString("id"),
                        rs.getString("action_instance_id"),
                        rs.getString("operation_type"),
                        rs.getString("operator"),
                        rs.getString("request_payload_json"),
                        rs.getString("result_status"),
                        rs.getString("result_message"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                actionInstanceId
        );
    }

    @Override
    public PageResult<AuditLogView> query(AuditLogQueryFilter filter) {
        int page = Math.max(1, filter.page());
        int size = Math.max(1, filter.size());
        List<Object> args = new ArrayList<>();
        String whereClause = buildWhereClause(filter, args);
        long total = jdbcTemplate.queryForObject(
                "select count(*) from action_ops_audit_log" + whereClause,
                Long.class,
                args.toArray()
        );
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add((page - 1) * size);
        List<AuditLogView> items = jdbcTemplate.query(
                "select id, action_instance_id, operation_type, operator, request_payload_json, result_status, result_message, created_at from action_ops_audit_log"
                        + whereClause
                        + " order by created_at desc limit ? offset ?",
                (rs, rowNum) -> new AuditLogView(
                        rs.getString("id"),
                        rs.getString("action_instance_id"),
                        rs.getString("operation_type"),
                        rs.getString("operator"),
                        rs.getString("request_payload_json"),
                        rs.getString("result_status"),
                        rs.getString("result_message"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                queryArgs.toArray()
        );
        return new PageResult<>(items, total, page, size);
    }

    private String buildWhereClause(AuditLogQueryFilter filter, List<Object> args) {
        List<String> conditions = new ArrayList<>();
        if (filter.actionInstanceId() != null && !filter.actionInstanceId().isBlank()) {
            conditions.add("action_instance_id = ?");
            args.add(filter.actionInstanceId());
        }
        if (filter.operationType() != null && !filter.operationType().isBlank()) {
            conditions.add("operation_type = ?");
            args.add(filter.operationType());
        }
        if (filter.operator() != null && !filter.operator().isBlank()) {
            conditions.add("operator = ?");
            args.add(filter.operator());
        }
        if (filter.createdFrom() != null) {
            conditions.add("created_at >= ?");
            args.add(Timestamp.from(filter.createdFrom()));
        }
        if (filter.createdTo() != null) {
            conditions.add("created_at <= ?");
            args.add(Timestamp.from(filter.createdTo()));
        }
        return conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
    }
}
