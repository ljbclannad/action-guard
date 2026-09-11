package io.github.actionguard.ops.api.repository.jdbc;

import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.ops.api.model.ActionOutboxView;
import io.github.actionguard.ops.api.repository.ActionOutboxQueryRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/** 使用运行时表提供 Outbox 的只读诊断查询。 */
public class JdbcActionOutboxQueryRepository implements ActionOutboxQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcActionOutboxQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ActionOutboxView> findByActionInstanceId(String actionInstanceId) {
        return jdbcTemplate.query(
                "select id, action_instance_id, topic, dispatch_id, status, available_at, attempt_count, "
                        + "delivery_attempt_count, version, created_at, updated_at "
                        + "from action_outbox where action_instance_id = ? order by created_at, id",
                (rs, rowNum) -> new ActionOutboxView(
                        rs.getString("id"),
                        rs.getString("action_instance_id"),
                        rs.getString("topic"),
                        rs.getString("dispatch_id"),
                        ActionOutboxStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("available_at").toInstant(),
                        rs.getInt("attempt_count"),
                        rs.getInt("delivery_attempt_count"),
                        rs.getInt("version"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                actionInstanceId
        );
    }
}
