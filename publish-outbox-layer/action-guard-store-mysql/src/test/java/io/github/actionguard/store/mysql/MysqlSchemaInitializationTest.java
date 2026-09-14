package io.github.actionguard.store.mysql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("初始化脚本的 H2 MySQL 模式回归验证，不替代真实 MySQL 验证")
class MysqlSchemaInitializationTest {

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:schema_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        jdbcTemplate = new JdbcTemplate(dataSource);
        initializeSchema();
    }

    @Test
    @DisplayName("完整脚本重复执行后保留九张表和已有数据")
    void shouldInitializeRepeatedlyWithoutChangingExistingData() {
        insertStep("step-1", "action-1", 0);

        initializeSchema();

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'PUBLIC' and table_type = 'BASE TABLE'
                """, Integer.class)).isEqualTo(9);
        assertThat(jdbcTemplate.queryForObject(
                "select step_name from action_step_instance where id = 'step-1'", String.class))
                .isEqualTo("发送通知");
        assertThat(jdbcTemplate.queryForObject("select count(*) from action_step_instance", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("同一动作的步骤索引不可重复，不同动作或不同索引不冲突")
    void shouldEnforceUniqueStepIndexWithinAction() {
        insertStep("step-1", "action-1", 0);
        initializeSchema();

        assertThatThrownBy(() -> insertStep("step-duplicate", "action-1", 0))
                .isInstanceOf(DuplicateKeyException.class);

        insertStep("step-2", "action-1", 1);
        insertStep("step-3", "action-2", 0);
        assertThat(jdbcTemplate.queryForObject("select count(*) from action_step_instance", Integer.class))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("所有业务表和字段均包含数据库注释")
    void shouldDocumentEveryTableAndColumn() {
        assertThat(jdbcTemplate.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'PUBLIC' and (remarks is null or trim(remarks) = '')
                """, String.class)).isEmpty();
        assertThat(jdbcTemplate.queryForList("""
                select column_name from information_schema.columns
                where table_schema = 'PUBLIC' and (remarks is null or trim(remarks) = '')
                """, String.class)).isEmpty();
    }

    private void initializeSchema() {
        new ResourceDatabasePopulator(new ClassPathResource("db/action-guard-mysql-schema.sql"))
                .execute(dataSource);
    }

    private void insertStep(String id, String actionInstanceId, int stepIndex) {
        jdbcTemplate.update("""
                insert into action_step_instance (
                    id, action_instance_id, step_index, step_name, step_type, target,
                    status, attempt_count, version, created_at, updated_at
                ) values (?, ?, ?, '发送通知', 'NOTIFY', 'order-paid', 'PENDING', 0, 0,
                          current_timestamp, current_timestamp)
                """, id, actionInstanceId, stepIndex);
    }
}
