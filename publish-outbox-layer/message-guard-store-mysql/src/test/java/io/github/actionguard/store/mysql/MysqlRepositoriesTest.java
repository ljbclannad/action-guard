package io.github.actionguard.store.mysql;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.core.model.ActionConsumeStatus;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.model.ActionStepStatus;
import io.github.actionguard.store.mysql.mapper.ActionConsumeLogMapper;
import io.github.actionguard.store.mysql.mapper.ActionInstanceMapper;
import io.github.actionguard.store.mysql.mapper.ActionOutboxMapper;
import io.github.actionguard.store.mysql.mapper.ActionStepInstanceMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MysqlRepositoriesTest {

    private DataSource dataSource;
    private SqlSessionTemplate sqlSessionTemplate;

    @BeforeEach
    void setUp() throws Exception {
        TestMysqlConfig config = loadTestMysqlConfig();
        String databaseName = "action_guard_test_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource adminDataSource = new DriverManagerDataSource();
        adminDataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        adminDataSource.setUrl("jdbc:mysql://" + config.host() + ":" + config.port() + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        adminDataSource.setUsername(config.username());
        adminDataSource.setPassword(config.password());
        JdbcTemplate adminJdbcTemplate = new JdbcTemplate(adminDataSource);
        adminJdbcTemplate.execute("create database if not exists `" + databaseName + "`");

        DriverManagerDataSource driverManagerDataSource = new DriverManagerDataSource();
        driverManagerDataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        driverManagerDataSource.setUrl("jdbc:mysql://" + config.host() + ":" + config.port() + "/" + databaseName + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        driverManagerDataSource.setUsername(config.username());
        driverManagerDataSource.setPassword(config.password());
        this.dataSource = driverManagerDataSource;

        new ResourceDatabasePopulator(new ClassPathResource("db/action-guard-mysql-schema.sql")).execute(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("delete from action_outbox");
        jdbcTemplate.update("delete from action_consume_log");
        jdbcTemplate.update("delete from action_step_instance");
        jdbcTemplate.update("delete from action_instance");

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new ClassPathResource("mapper/ActionInstanceMapper.xml"),
                new ClassPathResource("mapper/ActionStepInstanceMapper.xml"),
                new ClassPathResource("mapper/ActionOutboxMapper.xml"),
                new ClassPathResource("mapper/ActionConsumeLogMapper.xml")
        );
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        this.sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);
    }

    @Test
    void shouldPersistActionInstanceStepsAndOutbox() {
        ObjectMapper objectMapper = new ObjectMapper();
        MysqlActionInstanceRepository actionInstanceRepository = new MysqlActionInstanceRepository(mapper(ActionInstanceMapper.class), objectMapper);
        MysqlActionStepInstanceRepository actionStepInstanceRepository = new MysqlActionStepInstanceRepository(mapper(ActionStepInstanceMapper.class), objectMapper);
        MysqlActionOutboxRepository actionOutboxRepository = new MysqlActionOutboxRepository(mapper(ActionOutboxMapper.class));
        Instant now = Instant.parse("2026-06-26T07:45:00Z");

        ActionInstance actionInstance = new ActionInstance(
                "act-1",
                "order-cancel-flow",
                "order:1",
                ActionStatus.NEW,
                0,
                2,
                Map.of("operator", "demo"),
                null,
                null,
                0,
                now,
                now
        );
        List<ActionStepInstance> stepInstances = List.of(
                new ActionStepInstance("step-1", "act-1", 0, "send-cancel-event", "MQ_MESSAGE", "order.cancel.exchange", ActionStepStatus.PENDING, 0, Map.of("orderId", "1"), null, null, 0, now, now),
                new ActionStepInstance("step-2", "act-1", 1, "send-user-sms", "SMS", "notify.user", ActionStepStatus.PENDING, 0, Map.of("template", "order-cancel"), null, null, 0, now, now)
        );
        ActionOutbox outbox = new ActionOutbox("outbox-1", "act-1", "ACTION_EXECUTE", ActionOutboxStatus.NEW, now, 0, 0, now, now);

        actionInstanceRepository.save(actionInstance);
        actionStepInstanceRepository.saveAll(stepInstances);
        actionOutboxRepository.save(outbox);

        ActionInstance persisted = actionInstanceRepository.findByActionNameAndBizKey("order-cancel-flow", "order:1").orElseThrow();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThat(persisted.id()).isEqualTo("act-1");
        assertThat(persisted.attributes()).containsEntry("operator", "demo");
        assertThat(jdbcTemplate.queryForObject("select count(*) from action_step_instance where action_instance_id = 'act-1'", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select count(*) from action_outbox where action_instance_id = 'act-1'", Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldPersistConsumeLogAndFenceDuplicates() {
        MysqlActionConsumeLogRepository repository = new MysqlActionConsumeLogRepository(mapper(ActionConsumeLogMapper.class));
        Instant now = Instant.parse("2026-06-26T07:50:00Z");
        ActionExecutionMessage message = new ActionExecutionMessage(
                "ACTION_EXECUTE:outbox-1",
                "ACTION_EXECUTE:act-1",
                "outbox-1",
                "act-1",
                "ACTION_EXECUTE",
                now
        );

        assertThat(repository.tryStartConsumption(message, "rabbitmq-main", now)).isTrue();
        assertThat(repository.tryStartConsumption(message, "rabbitmq-main", now.plusSeconds(1))).isFalse();

        repository.markDuplicateSkipped(message.messageId(), "rabbitmq-main", now.plusSeconds(2));

        assertThat(repository.findByMessageId(message.messageId())).isPresent();
        assertThat(repository.findByMessageId(message.messageId()).orElseThrow().consumeStatus())
                .isEqualTo(ActionConsumeStatus.DUPLICATE_SKIPPED);
        assertThat(repository.findByMessageId(message.messageId()).orElseThrow().attemptCount()).isEqualTo(2);
    }

    @Test
    void shouldRejectStaleActionInstanceUpdate() {
        ObjectMapper objectMapper = new ObjectMapper();
        MysqlActionInstanceRepository repository = new MysqlActionInstanceRepository(mapper(ActionInstanceMapper.class), objectMapper);
        Instant now = Instant.parse("2026-06-26T08:00:00Z");
        ActionInstance original = new ActionInstance(
                "act-stale",
                "order-cancel-flow",
                "order:stale",
                ActionStatus.NEW,
                0,
                1,
                Map.of(),
                null,
                null,
                0,
                now,
                now
        );
        repository.save(original);
        repository.save(new ActionInstance(
                "act-stale",
                "order-cancel-flow",
                "order:stale",
                ActionStatus.DISPATCHING,
                0,
                1,
                Map.of(),
                null,
                null,
                0,
                now,
                now.plusSeconds(1)
        ));

        assertThatThrownBy(() -> repository.save(new ActionInstance(
                "act-stale",
                "order-cancel-flow",
                "order:stale",
                ActionStatus.SUCCESS,
                1,
                1,
                Map.of(),
                null,
                null,
                0,
                now,
                now.plusSeconds(2)
        ))).isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
    }

    private <T> T mapper(Class<T> mapperType) {
        return sqlSessionTemplate.getMapper(mapperType);
    }

    private TestMysqlConfig loadTestMysqlConfig() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        CompositePropertySource source = new CompositePropertySource("test-config");
        loader.load("application.yaml", new ClassPathResource("application.yaml")).forEach(source::addPropertySource);
        MutablePropertySources propertySources = new MutablePropertySources();
        propertySources.addFirst(source);
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources);
        return new TestMysqlConfig(
                envOrProperty("TEST_MYSQL_HOST", resolver, "spring.datasource.host"),
                envOrProperty("TEST_MYSQL_PORT", resolver, "spring.datasource.port"),
                envOrProperty("TEST_MYSQL_USERNAME", resolver, "spring.datasource.username"),
                envOrProperty("TEST_MYSQL_PASSWORD", resolver, "spring.datasource.password")
        );
    }

    private String envOrProperty(String envKey, PropertySourcesPropertyResolver resolver, String propertyKey) {
        String envValue = System.getenv(envKey);
        String value = envValue != null && !envValue.isBlank() ? envValue : resolver.getProperty(propertyKey);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required config: " + propertyKey);
        }
        return value;
    }

    private record TestMysqlConfig(String host, String port, String username, String password) {
    }
}
