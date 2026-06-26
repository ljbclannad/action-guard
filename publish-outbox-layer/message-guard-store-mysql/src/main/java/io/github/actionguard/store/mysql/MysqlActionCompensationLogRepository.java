package io.github.actionguard.store.mysql;

import io.github.actionguard.core.model.ActionCompensationLog;
import io.github.actionguard.core.repository.ActionCompensationLogRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.List;

public class MysqlActionCompensationLogRepository implements ActionCompensationLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public MysqlActionCompensationLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ActionCompensationLog save(ActionCompensationLog log) {
        jdbcTemplate.update(
                "insert into action_compensation_log (id, compensation_batch_id, action_instance_id, action_step_instance_id, step_index, step_name, step_type, compensation_status, compensator_name, result_message, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                log.id(),
                log.compensationBatchId(),
                log.actionInstanceId(),
                log.actionStepInstanceId(),
                log.stepIndex(),
                log.stepName(),
                log.stepType(),
                log.compensationStatus(),
                log.compensatorName(),
                log.resultMessage(),
                Timestamp.from(log.createdAt()),
                Timestamp.from(log.updatedAt())
        );
        return log;
    }

    @Override
    public List<ActionCompensationLog> findByActionInstanceId(String actionInstanceId) {
        return jdbcTemplate.query(
                "select id, compensation_batch_id, action_instance_id, action_step_instance_id, step_index, step_name, step_type, compensation_status, compensator_name, result_message, created_at, updated_at from action_compensation_log where action_instance_id = ? order by created_at asc",
                (rs, rowNum) -> new ActionCompensationLog(
                        rs.getString("id"),
                        rs.getString("compensation_batch_id"),
                        rs.getString("action_instance_id"),
                        rs.getString("action_step_instance_id"),
                        rs.getInt("step_index"),
                        rs.getString("step_name"),
                        rs.getString("step_type"),
                        rs.getString("compensation_status"),
                        rs.getString("compensator_name"),
                        rs.getString("result_message"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                actionInstanceId
        );
    }
}
