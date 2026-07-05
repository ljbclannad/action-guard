package io.github.actionguard.ops.api.repository.jdbc;

import io.github.actionguard.core.model.ActionConsumeStatus;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.model.ActionStepStatus;
import io.github.actionguard.ops.api.model.ActionDetailView;
import io.github.actionguard.ops.api.model.ActionListItem;
import io.github.actionguard.ops.api.model.ActionQueryFilter;
import io.github.actionguard.ops.api.model.ConsumeDetailView;
import io.github.actionguard.ops.api.model.PageResult;
import io.github.actionguard.ops.api.model.StepDetailView;
import io.github.actionguard.ops.api.repository.ActionOpsQueryRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcActionOpsQueryRepository implements ActionOpsQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcActionOpsQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PageResult<ActionListItem> queryActions(ActionQueryFilter filter) {
        int page = Math.max(1, filter.page());
        int size = Math.max(1, filter.size());
        List<Object> args = new ArrayList<>();
        String whereClause = buildWhereClause(filter, args);
        long total = jdbcTemplate.queryForObject(
                "select count(*) from action_instance" + whereClause,
                Long.class,
                args.toArray()
        );
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add((page - 1) * size);
        List<ActionListItem> items = jdbcTemplate.query(
                "select id, action_name, biz_key, status, current_step_index, total_step_count, last_error_code, last_error_message, created_at, updated_at from action_instance"
                        + whereClause
                        + " order by created_at desc limit ? offset ?",
                (rs, rowNum) -> new ActionListItem(
                        rs.getString("id"),
                        rs.getString("action_name"),
                        rs.getString("biz_key"),
                        ActionStatus.valueOf(rs.getString("status")),
                        rs.getInt("current_step_index"),
                        rs.getInt("total_step_count"),
                        rs.getString("last_error_code"),
                        rs.getString("last_error_message"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                queryArgs.toArray()
        );
        return new PageResult<>(items, total, page, size);
    }

    @Override
    public Optional<ActionDetailView> getActionDetail(String actionInstanceId) {
        List<ActionDetailView> details = jdbcTemplate.query(
                "select id, action_name, biz_key, status, current_step_index, total_step_count, last_error_code, last_error_message, created_at, updated_at from action_instance where id = ?",
                (rs, rowNum) -> new ActionDetailView(
                        rs.getString("id"),
                        rs.getString("action_name"),
                        rs.getString("biz_key"),
                        ActionStatus.valueOf(rs.getString("status")),
                        rs.getInt("current_step_index"),
                        rs.getInt("total_step_count"),
                        rs.getString("last_error_code"),
                        rs.getString("last_error_message"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant(),
                        getSteps(actionInstanceId),
                        getConsumes(actionInstanceId),
                        List.of()
                ),
                actionInstanceId
        );
        return details.stream().findFirst();
    }

    @Override
    public List<StepDetailView> getSteps(String actionInstanceId) {
        return jdbcTemplate.query(
                "select step_index, step_name, step_type, target, status, attempt_count, last_error_code, last_error_message, created_at, updated_at from action_step_instance where action_instance_id = ? order by step_index asc",
                (rs, rowNum) -> new StepDetailView(
                        rs.getInt("step_index"),
                        rs.getString("step_name"),
                        rs.getString("step_type"),
                        rs.getString("target"),
                        ActionStepStatus.valueOf(rs.getString("status")),
                        rs.getInt("attempt_count"),
                        rs.getString("last_error_code"),
                        rs.getString("last_error_message"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                actionInstanceId
        );
    }

    @Override
    public List<ConsumeDetailView> getConsumes(String actionInstanceId) {
        return jdbcTemplate.query(
                "select message_id, consumer_group, consume_status, attempt_count, last_error_message, first_received_at, last_received_at, updated_at from action_consume_log where action_instance_id = ? order by first_received_at asc",
                (rs, rowNum) -> new ConsumeDetailView(
                        rs.getString("message_id"),
                        rs.getString("consumer_group"),
                        ActionConsumeStatus.valueOf(rs.getString("consume_status")),
                        rs.getInt("attempt_count"),
                        rs.getString("last_error_message"),
                        rs.getTimestamp("first_received_at").toInstant(),
                        rs.getTimestamp("last_received_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                actionInstanceId
        );
    }

    private String buildWhereClause(ActionQueryFilter filter, List<Object> args) {
        List<String> conditions = new ArrayList<>();
        if (filter.actionName() != null && !filter.actionName().isBlank()) {
            conditions.add("action_name = ?");
            args.add(filter.actionName());
        }
        if (filter.bizKey() != null && !filter.bizKey().isBlank()) {
            conditions.add("biz_key = ?");
            args.add(filter.bizKey());
        }
        if (filter.status() != null && !filter.status().isBlank()) {
            conditions.add("status = ?");
            args.add(filter.status());
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
