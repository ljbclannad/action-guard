package io.github.actionguard.starter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.actionguard.api.ActionRequest;
import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.api.runtime.ActionRetryAction;
import io.github.actionguard.api.runtime.ActionStepContext;
import io.github.actionguard.api.runtime.StepExecutionResult;
import io.github.actionguard.api.spi.ActionAlertPublisher;
import io.github.actionguard.api.spi.ActionMetricsRecorder;
import io.github.actionguard.api.spi.ActionRetryPolicy;
import io.github.actionguard.api.spi.ActionStepHandler;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.model.ActionStepStatus;
import io.github.actionguard.core.model.ActionTransitionLog;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.repository.ActionStepInstanceRepository;
import io.github.actionguard.core.repository.ActionTransitionLogRepository;
import io.github.actionguard.core.runtime.definition.ActionDefinitionRegistry;
import io.github.actionguard.core.runtime.execution.ActionExecutionCallback;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageFactory;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import io.github.actionguard.core.runtime.publish.DefaultActionPublisher;
import io.github.actionguard.store.mysql.MysqlActionInstanceRepository;
import io.github.actionguard.store.mysql.MysqlActionOutboxRepository;
import io.github.actionguard.store.mysql.MysqlActionStepInstanceRepository;
import io.github.actionguard.store.mysql.MysqlActionTransitionLogRepository;
import io.github.actionguard.store.mysql.mapper.ActionInstanceMapper;
import io.github.actionguard.store.mysql.mapper.ActionOutboxMapper;
import io.github.actionguard.store.mysql.mapper.ActionStepInstanceMapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionExecutionTransactionTest {

    @Test
    void shouldRollbackEveryWriteAndReplayAfterPersistenceFailure() throws Exception {
        for (ActionRetryAction decision : List.of(ActionRetryAction.IMMEDIATE_RETRY, ActionRetryAction.DEAD)) {
            for (boolean success : List.of(true, false)) {
                for (String failure : List.of("step", "action", "log", "outbox")) {
                    if (!success && decision == ActionRetryAction.DEAD && failure.equals("outbox")) {
                        continue;
                    }
                    Fixture fixture = new Fixture();
                    fixture.result = success ? StepExecutionResult.succeeded() : StepExecutionResult.failed("FAILED", "下游失败");
                    fixture.decision = decision;
                    fixture.runner().run(context -> {
                        String id = fixture.publish(context.getBean(ActionDefinitionRegistry.class));
                        ActionInstance beforeAction = fixture.actions.findById(id).orElseThrow();
                        List<ActionStepInstance> beforeSteps = fixture.steps.findByActionInstanceId(id);
                        ActionOutbox beforeOutbox = fixture.outboxes.findByActionInstanceId(id).orElseThrow();
                        fixture.failure = failure;
                        ActionExecutionCallback callback = context.getBean(ActionExecutionCallback.class);

                        assertThatThrownBy(() -> callback.execute(fixture.message(id)))
                                .isInstanceOf(IllegalStateException.class).hasMessage("注入失败: " + failure);
                        assertThat(fixture.actions.findById(id).orElseThrow()).isEqualTo(beforeAction);
                        assertThat(fixture.steps.findByActionInstanceId(id)).isEqualTo(beforeSteps);
                        assertThat(fixture.outboxes.findByActionInstanceId(id).orElseThrow()).isEqualTo(beforeOutbox);
                        assertThat(fixture.logs.findByActionInstanceId(id)).isEmpty();
                        assertThat(fixture.sent).isEmpty();
                        assertThat(fixture.observations).isEmpty();

                        fixture.failure = null;
                        callback.execute(fixture.message(id));
                        assertThat(fixture.steps.findByActionInstanceId(id).get(0).attemptCount()).isEqualTo(1);
                        assertThat(fixture.logs.findByActionInstanceId(id)).hasSize(1);
                        assertThat(fixture.actions.findById(id).orElseThrow().status()).isEqualTo(
                                success ? ActionStatus.DISPATCHING : decision == ActionRetryAction.DEAD ? ActionStatus.FAILED : ActionStatus.RETRYING);
                    });
                }
            }
        }
    }

    @Test
    void shouldCommitNextStepBeforeSendingAndCompleteFinalStep() throws Exception {
        Fixture fixture = new Fixture();
        fixture.runner().run(context -> {
            String id = fixture.publish(context.getBean(ActionDefinitionRegistry.class));
            ActionExecutionCallback callback = context.getBean(ActionExecutionCallback.class);
            callback.execute(fixture.message(id));
            assertThat(fixture.sent).hasSize(1);
            assertThat(fixture.outboxes.findByActionInstanceId(id).orElseThrow().status()).isEqualTo(ActionOutboxStatus.DONE);
            callback.execute(fixture.message(id));
            assertThat(fixture.actions.findById(id).orElseThrow().status()).isEqualTo(ActionStatus.SUCCESS);
            assertThat(fixture.steps.findByActionInstanceId(id)).allMatch(step -> step.status() == ActionStepStatus.SUCCESS);
            assertThat(fixture.logs.findByActionInstanceId(id)).hasSize(2);
            assertThat(fixture.sent).hasSize(1);
        });
    }

    @Test
    void shouldSuspendCallerTransactionDuringHandlerAndCommitResultIndependently() throws Exception {
        Fixture fixture = new Fixture();
        fixture.runner().run(context -> {
            String id = fixture.publish(context.getBean(ActionDefinitionRegistry.class));
            new TransactionTemplate(fixture.transactions).executeWithoutResult(status -> {
                context.getBean(ActionExecutionCallback.class).execute(fixture.message(id));
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                status.setRollbackOnly();
            });
            assertThat(fixture.actions.findById(id).orElseThrow().currentStepIndex()).isEqualTo(1);
            assertThat(fixture.sent).hasSize(1);
        });
    }

    @Test
    void shouldKeepCommittedResultRecoverableWhenMessageSendFails() throws Exception {
        Fixture fixture = new Fixture();
        fixture.sendFails = true;
        fixture.runner().run(context -> {
            String id = fixture.publish(context.getBean(ActionDefinitionRegistry.class));
            context.getBean(ActionExecutionCallback.class).execute(fixture.message(id));
            assertThat(fixture.actions.findById(id).orElseThrow().currentStepIndex()).isEqualTo(1);
            assertThat(fixture.steps.findByActionInstanceId(id).get(0).status()).isEqualTo(ActionStepStatus.SUCCESS);
            assertThat(fixture.logs.findByActionInstanceId(id)).hasSize(1);
            assertThat(fixture.outboxes.findByActionInstanceId(id).orElseThrow().status()).isEqualTo(ActionOutboxStatus.NEW);
        });
    }

    @Test
    void shouldRollbackStepWhenActionVersionChangesDuringHandler() throws Exception {
        Fixture fixture = new Fixture();
        fixture.runner().run(context -> {
            String id = fixture.publish(context.getBean(ActionDefinitionRegistry.class));
            List<ActionStepInstance> beforeSteps = fixture.steps.findByActionInstanceId(id);
            ActionOutbox beforeOutbox = fixture.outboxes.findByActionInstanceId(id).orElseThrow();
            fixture.duringHandler = () -> fixture.jdbc.update("update action_instance set version = version + 1 where id = ?", id);

            assertThatThrownBy(() -> context.getBean(ActionExecutionCallback.class).execute(fixture.message(id)))
                    .isInstanceOf(OptimisticLockingFailureException.class);
            assertThat(fixture.steps.findByActionInstanceId(id)).isEqualTo(beforeSteps);
            assertThat(fixture.actions.findById(id).orElseThrow().version()).isEqualTo(1);
            assertThat(fixture.actions.findById(id).orElseThrow().currentStepIndex()).isZero();
            assertThat(fixture.outboxes.findByActionInstanceId(id).orElseThrow()).isEqualTo(beforeOutbox);
            assertThat(fixture.logs.findByActionInstanceId(id)).isEmpty();
            assertThat(fixture.sent).isEmpty();
        });
    }

    @Test
    void shouldRejectDatabaseRepositoriesWithoutTransactionManager() throws Exception {
        Fixture fixture = new Fixture();
        fixture.runnerWithoutTransactions().run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(
                    "数据库执行结果落库需要配置 PlatformTransactionManager，并与全部结果仓储使用同一数据源");
        });
    }

    @Test
    void shouldKeepResultAndDispatchWhenCommittedMetricsFail() throws Exception {
        Fixture fixture = new Fixture();
        fixture.metricsFail = true;
        fixture.runner().run(context -> {
            String id = fixture.publish(context.getBean(ActionDefinitionRegistry.class));
            context.getBean(ActionExecutionCallback.class).execute(fixture.message(id));
            assertThat(fixture.actions.findById(id).orElseThrow().currentStepIndex()).isEqualTo(1);
            assertThat(fixture.sent).hasSize(1);
            assertThat(fixture.outboxes.findByActionInstanceId(id).orElseThrow().status()).isEqualTo(ActionOutboxStatus.DONE);
        });
    }

    private static final class Fixture {
        private final DriverManagerDataSource dataSource;
        private final JdbcTemplate jdbc;
        private final PlatformTransactionManager transactions;
        private final MysqlActionInstanceRepository actions;
        private final MysqlActionStepInstanceRepository steps;
        private final MysqlActionOutboxRepository outboxes;
        private final MysqlActionTransitionLogRepository logs;
        private final List<ActionOutbox> sent = new ArrayList<>();
        private final List<String> observations = new ArrayList<>();
        private String failure;
        private boolean sendFails;
        private boolean metricsFail;
        private Runnable duringHandler = () -> { };
        private StepExecutionResult result = StepExecutionResult.succeeded();
        private ActionRetryAction decision = ActionRetryAction.IMMEDIATE_RETRY;

        private Fixture() throws Exception {
            dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
            jdbc = new JdbcTemplate(dataSource);
            transactions = new DataSourceTransactionManager(dataSource);
            new ResourceDatabasePopulator(new ClassPathResource("db/action-guard-mysql-schema.sql")).execute(dataSource);
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(new ClassPathResource("mapper/ActionInstanceMapper.xml"),
                    new ClassPathResource("mapper/ActionStepInstanceMapper.xml"), new ClassPathResource("mapper/ActionOutboxMapper.xml"));
            SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());
            ObjectMapper json = new ObjectMapper();
            actions = new MysqlActionInstanceRepository(session.getMapper(ActionInstanceMapper.class), json) {
                @Override
                public ActionInstance save(ActionInstance action) {
                    ActionInstance saved = super.save(action);
                    fail("action");
                    return saved;
                }
            };
            steps = new MysqlActionStepInstanceRepository(session.getMapper(ActionStepInstanceMapper.class), json) {
                @Override
                public ActionStepInstance save(ActionStepInstance step) {
                    ActionStepInstance saved = super.save(step);
                    fail("step");
                    return saved;
                }
            };
            outboxes = new MysqlActionOutboxRepository(session.getMapper(ActionOutboxMapper.class)) {
                @Override
                public ActionOutbox save(ActionOutbox outbox) {
                    ActionOutbox saved = super.save(outbox);
                    fail("outbox");
                    return saved;
                }
            };
            logs = new MysqlActionTransitionLogRepository(jdbc) {
                @Override
                public ActionTransitionLog save(ActionTransitionLog log) {
                    ActionTransitionLog saved = super.save(log);
                    fail("log");
                    return saved;
                }
            };
        }

        private void fail(String stage) {
            if (stage.equals(failure)) {
                throw new IllegalStateException("注入失败: " + stage);
            }
        }

        private ApplicationContextRunner runner() {
            return runnerWithoutTransactions().withBean(PlatformTransactionManager.class, () -> transactions);
        }

        private ApplicationContextRunner runnerWithoutTransactions() {
            return new ApplicationContextRunner()
                    .withPropertyValues("action.guard.store.type=memory")
                    .withConfiguration(AutoConfigurations.of(ActionGuardAutoConfiguration.class))
                    .withPropertyValues("action.guard.definition-locations=classpath:actions/order-cancel.yml")
                    .withBean(ActionInstanceRepository.class, () -> actions)
                    .withBean(ActionStepInstanceRepository.class, () -> steps)
                    .withBean(ActionOutboxRepository.class, () -> outboxes)
                    .withBean(ActionTransitionLogRepository.class, () -> logs)
                    .withBean(ActionRetryPolicy.class, () -> (error, context) -> decision)
                    .withBean(ActionMetricsRecorder.class, () -> (name, tags) -> {
                        observations.add(name);
                        if (metricsFail) {
                            throw new IllegalStateException("指标通道失败");
                        }
                    })
                    .withBean(ActionAlertPublisher.class, () -> event -> observations.add(event.type().name()))
                    .withBean("mqHandler", ActionStepHandler.class, () -> handler("MQ_MESSAGE"))
                    .withBean("smsHandler", ActionStepHandler.class, () -> handler("SMS"))
                    .withBean(ActionExecutionMessageProducer.class, () -> outbox -> {
                        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                        // 新连接读取必须能看到整组提交结果，避免仅验证同一事务内的可见性。
                        JdbcTemplate independent = new JdbcTemplate(new DriverManagerDataSource(dataSource.getUrl(), "sa", ""));
                        assertThat(independent.queryForObject("select count(*) from action_transition_log where action_instance_id = ?",
                                Integer.class, outbox.actionInstanceId())).isGreaterThan(0);
                        assertThat(independent.queryForObject("select dispatch_id from action_outbox where id = ?",
                                String.class, outbox.id())).isEqualTo(outbox.dispatchId());
                        sent.add(outbox);
                        if (sendFails) {
                            throw new IllegalStateException("发送失败");
                        }
                    });
        }

        private ActionStepHandler handler(String type) {
            return new ActionStepHandler() {
                @Override
                public String stepType() {
                    return type;
                }

                @Override
                public StepExecutionResult execute(ActionStepContext context) {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    duringHandler.run();
                    return result;
                }
            };
        }

        private String publish(ActionDefinitionRegistry registry) {
            return new DefaultActionPublisher(registry, actions, steps, outboxes, Clock.systemUTC())
                    .publish(new ActionRequest("order-cancel-flow", "order:1", Map.of(), List.of())).actionInstanceId();
        }

        private ActionExecutionMessage message(String id) {
            return new ActionExecutionMessageFactory().create(outboxes.findByActionInstanceId(id).orElseThrow());
        }
    }
}
