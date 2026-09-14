package io.github.actionguard.ops.api.repository.jdbc;

import io.github.actionguard.core.model.ActionAlertOutboxStatus;
import io.github.actionguard.ops.api.model.ActionAlertOutboxView;
import io.github.actionguard.ops.api.repository.ActionAlertOutboxQueryRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 使用运行时 alert outbox 表提供 Action 告警投递诊断。
 */
public class JdbcActionAlertOutboxQueryRepository implements ActionAlertOutboxQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcActionAlertOutboxQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ActionAlertOutboxView> findByActionInstanceId(String actionInstanceId) {
        return jdbcTemplate.query(
                "select id, event_id, type, level, action_name, action_instance_id, step_name, step_type, status, "
                        + "available_at, delivery_attempt_count, last_error_message, occurred_at, created_at, updated_at, version "
                        + "from action_alert_outbox where action_instance_id = ? order by created_at, id",
                (rs, rowNum) -> new ActionAlertOutboxView(
                        rs.getString("id"), rs.getString("event_id"), rs.getString("type"), rs.getString("level"),
                        rs.getString("action_name"), rs.getString("action_instance_id"), rs.getString("step_name"),
                        rs.getString("step_type"), ActionAlertOutboxStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("available_at").toInstant(), rs.getInt("delivery_attempt_count"),
                        rs.getString("last_error_message"), rs.getTimestamp("occurred_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), rs.getInt("version")
                ),
                actionInstanceId
        );
    }
}
