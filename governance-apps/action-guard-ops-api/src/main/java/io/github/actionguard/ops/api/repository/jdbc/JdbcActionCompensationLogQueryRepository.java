package io.github.actionguard.ops.api.repository.jdbc;

import io.github.actionguard.ops.api.model.CompensationLogView;
import io.github.actionguard.ops.api.repository.ActionCompensationLogQueryRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

public class JdbcActionCompensationLogQueryRepository implements ActionCompensationLogQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcActionCompensationLogQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<CompensationLogView> findByActionInstanceId(String actionInstanceId) {
        return jdbcTemplate.query(
                "select compensation_batch_id, step_index, step_name, step_type, compensation_status, compensator_name, result_message, created_at from action_compensation_log where action_instance_id = ? order by compensation_batch_id desc, step_index desc",
                (rs, rowNum) -> new CompensationLogView(
                        rs.getString("compensation_batch_id"),
                        rs.getInt("step_index"),
                        rs.getString("step_name"),
                        rs.getString("step_type"),
                        rs.getString("compensation_status"),
                        rs.getString("compensator_name"),
                        rs.getString("result_message"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                actionInstanceId
        );
    }
}
