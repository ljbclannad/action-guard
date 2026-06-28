package io.github.actionguard.starter.config;

import io.github.actionguard.api.ActionPublisher;
import io.github.actionguard.api.ActionRequest;
import io.github.actionguard.api.definition.ActionDefinition;
import io.github.actionguard.api.runtime.ActionStepContext;
import io.github.actionguard.api.runtime.StepExecutionResult;
import io.github.actionguard.api.spi.ActionStepHandler;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.repository.ActionConsumeLogRepository;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.repository.ActionStepInstanceRepository;
import io.github.actionguard.core.runtime.definition.ActionDefinitionRegistry;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import io.github.actionguard.core.runtime.registry.StepHandlerRegistry;
import io.github.actionguard.api.spi.ActionMetricsRecorder;
import io.github.actionguard.starter.metrics.InMemoryActionMetricsRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionGuardAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestStepHandlerConfiguration.class)
            .withConfiguration(AutoConfigurations.of(ActionGuardAutoConfiguration.class));

    @Test
    void shouldCollectActionStepHandlersIntoRegistry() {
        contextRunner.run(context -> {
            StepHandlerRegistry registry = context.getBean(StepHandlerRegistry.class);

            assertThat(registry.find("SMS")).isPresent();
            assertThat(registry.find("EMAIL")).isPresent();
        });
    }

    @Test
    void shouldLoadYamlDefinitionsIntoRegistry() {
        contextRunner
                .withPropertyValues("action.guard.definition-locations=classpath*:actions/*.yml")
                .run(context -> {
                    ActionDefinitionRegistry registry = context.getBean(ActionDefinitionRegistry.class);

                    ActionDefinition definition = registry.getRequired("order-cancel-flow");
                    assertThat(definition.steps()).hasSize(2);
                    assertThat(definition.steps().get(0).stepType()).isEqualTo("MQ_MESSAGE");
                });
    }

    @Test
    void shouldRollbackActionAndStepWritesWhenOutboxWriteFails() {
        new ApplicationContextRunner()
                .withUserConfiguration(TransactionTestConfiguration.class)
                .withConfiguration(AutoConfigurations.of(ActionGuardAutoConfiguration.class))
                .withPropertyValues("action.guard.definition-locations=classpath*:actions/*.yml")
                .run(context -> {
                    ActionPublisher actionPublisher = context.getBean(ActionPublisher.class);
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);

                    assertThatThrownBy(() -> actionPublisher.publish(new ActionRequest(
                            "order-cancel-flow",
                            "order:rollback",
                            Map.of("operator", "demo"),
                            List.of()
                    )))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("simulated outbox failure");

                    assertThat(jdbcTemplate.queryForObject("select count(*) from action_instance", Integer.class)).isZero();
                    assertThat(jdbcTemplate.queryForObject("select count(*) from action_step_instance", Integer.class)).isZero();
                });
    }

    @Test
    void shouldPublishOutboxMessageAfterTransactionCommit() {
        new ApplicationContextRunner()
                .withUserConfiguration(TransactionDispatchTestConfiguration.class)
                .withConfiguration(AutoConfigurations.of(ActionGuardAutoConfiguration.class))
                .withPropertyValues("action.guard.definition-locations=classpath*:actions/*.yml")
                .run(context -> {
                    ActionPublisher actionPublisher = context.getBean(ActionPublisher.class);
                    CapturingActionExecutionMessageProducer producer = context.getBean(CapturingActionExecutionMessageProducer.class);

                    actionPublisher.publish(new ActionRequest(
                            "order-cancel-flow",
                            "order:auto-dispatch",
                            Map.of("operator", "demo"),
                            List.of()
                    ));

                    assertThat(producer.published()).hasSize(1);
                    assertThat(producer.published().get(0).topic()).isEqualTo("ACTION_EXECUTE");
                    ActionOutbox persistedOutbox = context.getBean(ActionOutboxRepository.class)
                            .findByActionInstanceId(producer.published().get(0).actionInstanceId())
                            .orElseThrow();
                    assertThat(persistedOutbox.status()).isEqualTo(ActionOutboxStatus.DONE);
                });
    }

    @Test
    void shouldNotPublishOutboxMessageWhenTransactionRollsBack() {
        new ApplicationContextRunner()
                .withUserConfiguration(TransactionDispatchRollbackTestConfiguration.class)
                .withConfiguration(AutoConfigurations.of(ActionGuardAutoConfiguration.class))
                .withPropertyValues("action.guard.definition-locations=classpath*:actions/*.yml")
                .run(context -> {
                    ActionPublisher actionPublisher = context.getBean(ActionPublisher.class);
                    CapturingActionExecutionMessageProducer producer = context.getBean(CapturingActionExecutionMessageProducer.class);

                    assertThatThrownBy(() -> actionPublisher.publish(new ActionRequest(
                            "order-cancel-flow",
                            "order:dispatch-rollback",
                            Map.of("operator", "demo"),
                            List.of()
                    )))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("simulated post-persist failure");

                    assertThat(producer.published()).isEmpty();
                });
    }

    @Test
    void shouldMarkOutboxDeadWhenPublishAfterCommitFails() {
        new ApplicationContextRunner()
                .withUserConfiguration(TransactionDispatchFailureTestConfiguration.class)
                .withConfiguration(AutoConfigurations.of(ActionGuardAutoConfiguration.class))
                .withPropertyValues("action.guard.definition-locations=classpath*:actions/*.yml")
                .run(context -> {
                    ActionPublisher actionPublisher = context.getBean(ActionPublisher.class);
                    FailingActionExecutionMessageProducer producer = context.getBean(FailingActionExecutionMessageProducer.class);

                    assertThatThrownBy(() -> actionPublisher.publish(new ActionRequest(
                            "order-cancel-flow",
                            "order:dispatch-failure",
                            Map.of("operator", "demo"),
                            List.of()
                    )))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("simulated producer failure");

                    assertThat(producer.attempted()).isEqualTo(1);
                    ActionInstance actionInstance = context.getBean(ActionInstanceRepository.class)
                            .findByActionNameAndBizKey("order-cancel-flow", "order:dispatch-failure")
                            .orElseThrow();
                    ActionOutbox persistedOutbox = context.getBean(ActionOutboxRepository.class)
                            .findByActionInstanceId(actionInstance.id())
                            .orElseThrow();
                    assertThat(persistedOutbox.status()).isEqualTo(ActionOutboxStatus.DEAD);
                });
    }

    @Test
    void shouldRetryOutboxPublishBeforeMarkingDone() {
        new ApplicationContextRunner()
                .withUserConfiguration(TransactionDispatchRetrySuccessTestConfiguration.class)
                .withConfiguration(AutoConfigurations.of(ActionGuardAutoConfiguration.class))
                .withPropertyValues(
                        "action.guard.definition-locations=classpath*:actions/*.yml",
                        "action.guard.publish-retry-max-attempts=2"
                )
                .run(context -> {
                    ActionPublisher actionPublisher = context.getBean(ActionPublisher.class);
                    RetryOnceActionExecutionMessageProducer producer = context.getBean(RetryOnceActionExecutionMessageProducer.class);

                    actionPublisher.publish(new ActionRequest(
                            "order-cancel-flow",
                            "order:dispatch-retry-success",
                            Map.of("operator", "demo"),
                            List.of()
                    ));

                    assertThat(producer.attempted()).isEqualTo(2);
                    ActionInstance actionInstance = context.getBean(ActionInstanceRepository.class)
                            .findByActionNameAndBizKey("order-cancel-flow", "order:dispatch-retry-success")
                            .orElseThrow();
                    ActionOutbox persistedOutbox = context.getBean(ActionOutboxRepository.class)
                            .findByActionInstanceId(actionInstance.id())
                            .orElseThrow();
                    assertThat(persistedOutbox.status()).isEqualTo(ActionOutboxStatus.DONE);
                    assertThat(persistedOutbox.attemptCount()).isEqualTo(1);
                });
    }

    @Test
    void shouldMarkOutboxDeadAfterRetryAttemptsExhausted() {
        new ApplicationContextRunner()
                .withUserConfiguration(TransactionDispatchRetryExhaustedTestConfiguration.class)
                .withConfiguration(AutoConfigurations.of(ActionGuardAutoConfiguration.class))
                .withPropertyValues(
                        "action.guard.definition-locations=classpath*:actions/*.yml",
                        "action.guard.publish-retry-max-attempts=2"
                )
                .run(context -> {
                    ActionPublisher actionPublisher = context.getBean(ActionPublisher.class);
                    FailingActionExecutionMessageProducer producer = context.getBean(FailingActionExecutionMessageProducer.class);

                    assertThatThrownBy(() -> actionPublisher.publish(new ActionRequest(
                            "order-cancel-flow",
                            "order:dispatch-retry-dead",
                            Map.of("operator", "demo"),
                            List.of()
                    )))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("simulated producer failure");

                    assertThat(producer.attempted()).isEqualTo(2);
                    ActionInstance actionInstance = context.getBean(ActionInstanceRepository.class)
                            .findByActionNameAndBizKey("order-cancel-flow", "order:dispatch-retry-dead")
                            .orElseThrow();
                    ActionOutbox persistedOutbox = context.getBean(ActionOutboxRepository.class)
                            .findByActionInstanceId(actionInstance.id())
                            .orElseThrow();
                    assertThat(persistedOutbox.status()).isEqualTo(ActionOutboxStatus.DEAD);
                    assertThat(persistedOutbox.attemptCount()).isEqualTo(2);
                });
    }

    @Test
    void shouldExposeInMemoryMetricsRecorderByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ActionMetricsRecorder.class);
            assertThat(context.getBean(ActionMetricsRecorder.class)).isInstanceOf(InMemoryActionMetricsRecorder.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestStepHandlerConfiguration {

        @Bean
        ActionStepHandler smsActionStepHandler() {
            return new TestActionStepHandler("SMS");
        }

        @Bean
        ActionStepHandler emailActionStepHandler() {
            return new TestActionStepHandler("EMAIL");
        }
    }

    @EnableTransactionManagement
    @Configuration(proxyBeanMethods = false)
    static class TransactionTestConfiguration {

        @Bean
        ActionStepHandler smsActionStepHandler() {
            return new TestActionStepHandler("SMS");
        }

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:action_guard_tx;MODE=MySQL;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.execute("""
                    create table action_instance (
                        id varchar(64) primary key,
                        action_name varchar(128) not null,
                        biz_key varchar(256) not null,
                        status varchar(32) not null,
                        current_step_index int not null,
                        total_step_count int not null,
                        attributes_json clob,
                        last_error_code varchar(128),
                        last_error_message clob,
                        created_at timestamp not null,
                        updated_at timestamp not null
                    )
                    """);
            jdbcTemplate.execute("""
                    create table action_step_instance (
                        id varchar(64) primary key,
                        action_instance_id varchar(64) not null,
                        step_index int not null,
                        step_name varchar(128) not null,
                        step_type varchar(128) not null,
                        target varchar(256) not null,
                        status varchar(32) not null,
                        attempt_count int not null,
                        payload_json clob,
                        last_error_code varchar(128),
                        last_error_message clob,
                        created_at timestamp not null,
                        updated_at timestamp not null
                    )
                    """);
            return jdbcTemplate;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        ActionConsumeLogRepository actionConsumeLogRepository() {
            return new io.github.actionguard.core.repository.InMemoryActionConsumeLogRepository();
        }

        @Bean
        ActionInstanceRepository actionInstanceRepository(JdbcTemplate jdbcTemplate) {
            return new ActionInstanceRepository() {
                @Override
                public Optional<ActionInstance> findById(String id) {
                    return Optional.empty();
                }

                @Override
                public Optional<ActionInstance> findByActionNameAndBizKey(String actionName, String bizKey) {
                    return Optional.empty();
                }

                @Override
                public ActionInstance save(ActionInstance instance) {
                    jdbcTemplate.update(
                            "insert into action_instance (id, action_name, biz_key, status, current_step_index, total_step_count, attributes_json, last_error_code, last_error_message, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            instance.id(),
                            instance.actionName(),
                            instance.bizKey(),
                            instance.status().name(),
                            instance.currentStepIndex(),
                            instance.totalStepCount(),
                            "{}",
                            instance.lastErrorCode(),
                            instance.lastErrorMessage(),
                            Timestamp.from(instance.createdAt()),
                            Timestamp.from(instance.updatedAt())
                    );
                    return instance;
                }
            };
        }

        @Bean
        ActionStepInstanceRepository actionStepInstanceRepository(JdbcTemplate jdbcTemplate) {
            return new ActionStepInstanceRepository() {
                @Override
                public ActionStepInstance save(ActionStepInstance step) {
                    jdbcTemplate.update(
                            "insert into action_step_instance (id, action_instance_id, step_index, step_name, step_type, target, status, attempt_count, payload_json, last_error_code, last_error_message, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            step.id(),
                            step.actionInstanceId(),
                            step.stepIndex(),
                            step.stepName(),
                            step.stepType(),
                            step.target(),
                            step.status().name(),
                            step.attemptCount(),
                            "{}",
                            step.lastErrorCode(),
                            step.lastErrorMessage(),
                            Timestamp.from(step.createdAt()),
                            Timestamp.from(step.updatedAt())
                    );
                    return step;
                }

                @Override
                public List<ActionStepInstance> saveAll(List<ActionStepInstance> stepInstances) {
                    stepInstances.forEach(this::save);
                    return stepInstances;
                }

                @Override
                public List<ActionStepInstance> findByActionInstanceId(String actionInstanceId) {
                    return List.of();
                }
            };
        }

        @Bean
        ActionOutboxRepository actionOutboxRepository() {
            return new ActionOutboxRepository() {
                @Override
                public io.github.actionguard.core.model.ActionOutbox save(io.github.actionguard.core.model.ActionOutbox outbox) {
                    throw new IllegalStateException("simulated outbox failure");
                }

                @Override
                public Optional<io.github.actionguard.core.model.ActionOutbox> findByActionInstanceId(String actionInstanceId) {
                    return Optional.empty();
                }
            };
        }
    }

    @EnableTransactionManagement
    @Configuration(proxyBeanMethods = false)
    static class TransactionDispatchTestConfiguration {

        @Bean
        ActionStepHandler smsActionStepHandler() {
            return new TestActionStepHandler("SMS");
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new NoOpTransactionManager();
        }

        @Bean
        ActionConsumeLogRepository actionConsumeLogRepository() {
            return new io.github.actionguard.core.repository.InMemoryActionConsumeLogRepository();
        }

        @Bean
        ActionExecutionMessageProducer actionExecutionMessageProducer() {
            return new CapturingActionExecutionMessageProducer();
        }
    }

    @EnableTransactionManagement
    @Configuration(proxyBeanMethods = false)
    static class TransactionDispatchRollbackTestConfiguration {

        @Bean
        ActionStepHandler smsActionStepHandler() {
            return new TestActionStepHandler("SMS");
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new NoOpTransactionManager();
        }

        @Bean
        ActionConsumeLogRepository actionConsumeLogRepository() {
            return new io.github.actionguard.core.repository.InMemoryActionConsumeLogRepository();
        }

        @Bean
        ActionExecutionMessageProducer actionExecutionMessageProducer() {
            return new CapturingActionExecutionMessageProducer();
        }

        @Bean
        ActionInstanceRepository actionInstanceRepository() {
            return new InMemoryFailingActionInstanceRepository();
        }
    }

    @EnableTransactionManagement
    @Configuration(proxyBeanMethods = false)
    static class TransactionDispatchFailureTestConfiguration {

        @Bean
        ActionStepHandler smsActionStepHandler() {
            return new TestActionStepHandler("SMS");
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new NoOpTransactionManager();
        }

        @Bean
        ActionConsumeLogRepository actionConsumeLogRepository() {
            return new io.github.actionguard.core.repository.InMemoryActionConsumeLogRepository();
        }

        @Bean
        ActionExecutionMessageProducer actionExecutionMessageProducer() {
            return new FailingActionExecutionMessageProducer();
        }
    }

    @EnableTransactionManagement
    @Configuration(proxyBeanMethods = false)
    static class TransactionDispatchRetrySuccessTestConfiguration {

        @Bean
        ActionStepHandler smsActionStepHandler() {
            return new TestActionStepHandler("SMS");
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new NoOpTransactionManager();
        }

        @Bean
        ActionConsumeLogRepository actionConsumeLogRepository() {
            return new io.github.actionguard.core.repository.InMemoryActionConsumeLogRepository();
        }

        @Bean
        ActionExecutionMessageProducer actionExecutionMessageProducer() {
            return new RetryOnceActionExecutionMessageProducer();
        }
    }

    @EnableTransactionManagement
    @Configuration(proxyBeanMethods = false)
    static class TransactionDispatchRetryExhaustedTestConfiguration {

        @Bean
        ActionStepHandler smsActionStepHandler() {
            return new TestActionStepHandler("SMS");
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new NoOpTransactionManager();
        }

        @Bean
        ActionConsumeLogRepository actionConsumeLogRepository() {
            return new io.github.actionguard.core.repository.InMemoryActionConsumeLogRepository();
        }

        @Bean
        ActionExecutionMessageProducer actionExecutionMessageProducer() {
            return new FailingActionExecutionMessageProducer();
        }
    }

    static class CapturingActionExecutionMessageProducer implements ActionExecutionMessageProducer {
        private final List<ActionOutbox> published = new ArrayList<>();

        @Override
        public void publish(ActionOutbox outbox) {
            published.add(outbox);
        }

        List<ActionOutbox> published() {
            return List.copyOf(published);
        }
    }

    static class FailingActionExecutionMessageProducer implements ActionExecutionMessageProducer {
        private int attempted;

        @Override
        public void publish(ActionOutbox outbox) {
            attempted++;
            throw new IllegalStateException("simulated producer failure");
        }

        int attempted() {
            return attempted;
        }
    }

    static class RetryOnceActionExecutionMessageProducer implements ActionExecutionMessageProducer {
        private int attempted;

        @Override
        public void publish(ActionOutbox outbox) {
            attempted++;
            if (attempted == 1) {
                throw new IllegalStateException("simulated producer failure");
            }
        }

        int attempted() {
            return attempted;
        }
    }

    static class InMemoryFailingActionInstanceRepository extends io.github.actionguard.core.repository.InMemoryActionInstanceRepository {
        @Override
        public ActionInstance save(ActionInstance instance) {
            ActionInstance saved = super.save(instance);
            if (saved.currentStepIndex() == 0 && saved.createdAt().equals(saved.updatedAt()) && saved.lastErrorCode() == null) {
                throw new IllegalStateException("simulated post-persist failure");
            }
            return saved;
        }
    }

    static class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    private record TestActionStepHandler(String stepType) implements ActionStepHandler {

        @Override
        public StepExecutionResult execute(ActionStepContext context) {
            return StepExecutionResult.succeeded();
        }
    }
}
