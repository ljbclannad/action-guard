package io.github.actionguard.store.mysql;

import io.github.actionguard.core.model.ActionGovernancePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MysqlActionGovernancePolicyRepositoryTest {

    @Test
    void shouldPersistAndLoadActionGovernancePolicy() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:governance_policy;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                create table if not exists action_governance_policy (
                    id varchar(64) primary key,
                    action_name varchar(128) not null,
                    compensation_enabled tinyint null,
                    retry_policy_json text,
                    alert_policy_json text,
                    updated_at timestamp not null
                )
                """);
        jdbcTemplate.execute("create unique index if not exists uk_action_governance_policy_name on action_governance_policy (action_name)");

        MysqlActionGovernancePolicyRepository repository = new MysqlActionGovernancePolicyRepository(jdbcTemplate);
        Instant now = Instant.parse("2026-06-26T12:00:00Z");
        repository.save(new ActionGovernancePolicy(
                "policy-1",
                "order-cancel-flow",
                Boolean.TRUE,
                null,
                null,
                now
        ));

        assertThat(repository.findByActionName("order-cancel-flow")).isPresent();
        assertThat(repository.findByActionName("order-cancel-flow").orElseThrow().compensationEnabled()).isTrue();
        assertThat(jdbcTemplate.queryForObject("select updated_at from action_governance_policy where action_name = 'order-cancel-flow'", Timestamp.class))
                .isNotNull();
    }
}
