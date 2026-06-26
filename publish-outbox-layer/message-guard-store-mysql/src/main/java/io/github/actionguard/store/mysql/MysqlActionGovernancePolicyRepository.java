package io.github.actionguard.store.mysql;

import io.github.actionguard.core.model.ActionGovernancePolicy;
import io.github.actionguard.core.repository.ActionGovernancePolicyRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class MysqlActionGovernancePolicyRepository implements ActionGovernancePolicyRepository {

    private final JdbcTemplate jdbcTemplate;

    public MysqlActionGovernancePolicyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ActionGovernancePolicy> findByActionName(String actionName) {
        List<ActionGovernancePolicy> results = jdbcTemplate.query(
                "select id, action_name, compensation_enabled, retry_policy_json, alert_policy_json, updated_at from action_governance_policy where action_name = ?",
                (rs, rowNum) -> new ActionGovernancePolicy(
                        rs.getString("id"),
                        rs.getString("action_name"),
                        rs.getObject("compensation_enabled") == null ? null : rs.getBoolean("compensation_enabled"),
                        rs.getString("retry_policy_json"),
                        rs.getString("alert_policy_json"),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                actionName
        );
        return results.stream().findFirst();
    }

    @Override
    public ActionGovernancePolicy save(ActionGovernancePolicy policy) {
        if (findByActionName(policy.actionName()).isPresent()) {
            jdbcTemplate.update(
                    "update action_governance_policy set compensation_enabled = ?, retry_policy_json = ?, alert_policy_json = ?, updated_at = ? where action_name = ?",
                    policy.compensationEnabled(),
                    policy.retryPolicyJson(),
                    policy.alertPolicyJson(),
                    Timestamp.from(policy.updatedAt()),
                    policy.actionName()
            );
            return policy;
        }
        jdbcTemplate.update(
                "insert into action_governance_policy (id, action_name, compensation_enabled, retry_policy_json, alert_policy_json, updated_at) values (?, ?, ?, ?, ?, ?)",
                policy.id(),
                policy.actionName(),
                policy.compensationEnabled(),
                policy.retryPolicyJson(),
                policy.alertPolicyJson(),
                Timestamp.from(policy.updatedAt())
        );
        return policy;
    }
}
