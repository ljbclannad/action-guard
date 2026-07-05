package io.github.actionguard.store.mysql;

import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.model.ActionTransitionLog;
import io.github.actionguard.core.repository.ActionTransitionLogRepository;
import io.github.actionguard.core.runtime.state.ActionTransitionEvent;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.List;

public class MysqlActionTransitionLogRepository implements ActionTransitionLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public MysqlActionTransitionLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ActionTransitionLog save(ActionTransitionLog log) {
        jdbcTemplate.update(
                "insert into action_transition_log (id, action_instance_id, transition_event, from_status, to_status, step_index, step_name, step_type, operator, error_code, error_message, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                log.id(),
                log.actionInstanceId(),
                log.event().name(),
                log.fromStatus().name(),
                log.toStatus().name(),
                log.stepIndex(),
                log.stepName(),
                log.stepType(),
                log.operator(),
                log.errorCode(),
                log.errorMessage(),
                Timestamp.from(log.createdAt())
        );
        return log;
    }

    @Override
    public List<ActionTransitionLog> findByActionInstanceId(String actionInstanceId) {
        return jdbcTemplate.query(
                "select id, action_instance_id, transition_event, from_status, to_status, step_index, step_name, step_type, operator, error_code, error_message, created_at from action_transition_log where action_instance_id = ? order by created_at asc",
                (rs, rowNum) -> new ActionTransitionLog(
                        rs.getString("id"),
                        rs.getString("action_instance_id"),
                        ActionTransitionEvent.valueOf(rs.getString("transition_event")),
                        ActionStatus.valueOf(rs.getString("from_status")),
                        ActionStatus.valueOf(rs.getString("to_status")),
                        (Integer) rs.getObject("step_index"),
                        rs.getString("step_name"),
                        rs.getString("step_type"),
                        rs.getString("operator"),
                        rs.getString("error_code"),
                        rs.getString("error_message"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                actionInstanceId
        );
    }
}
