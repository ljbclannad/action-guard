package io.github.actionguard.ops.api.repository.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

final class TestAuditLogRepositoryFactory {

    private TestAuditLogRepositoryFactory() {
    }

    static JdbcActionAuditLogRepository create() {
        DataSource dataSource = dataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                create table if not exists action_ops_audit_log (
                    id varchar(64) primary key,
                    action_instance_id varchar(64) not null,
                    operation_type varchar(64) not null,
                    operator varchar(128) not null,
                    request_payload_json text,
                    result_status varchar(32) not null,
                    result_message text,
                    created_at timestamp not null
                )
                """);
        jdbcTemplate.execute("create index if not exists idx_action_ops_audit_log_action on action_ops_audit_log (action_instance_id, created_at)");
        jdbcTemplate.execute("create index if not exists idx_action_ops_audit_log_operator on action_ops_audit_log (operator, created_at)");
        return new JdbcActionAuditLogRepository(jdbcTemplate);
    }

    private static DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:ops_audit_log;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
