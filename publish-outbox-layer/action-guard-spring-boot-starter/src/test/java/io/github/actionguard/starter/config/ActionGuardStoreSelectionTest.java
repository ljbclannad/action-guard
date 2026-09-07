package io.github.actionguard.starter.config;

import io.github.actionguard.core.repository.ActionCompensationLogRepository;
import io.github.actionguard.core.repository.ActionConsumeLogRepository;
import io.github.actionguard.core.repository.ActionGovernancePolicyRepository;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.repository.ActionStepInstanceRepository;
import io.github.actionguard.core.repository.ActionTransitionLogRepository;
import io.github.actionguard.core.repository.InMemoryActionCompensationLogRepository;
import io.github.actionguard.core.repository.InMemoryActionConsumeLogRepository;
import io.github.actionguard.core.repository.InMemoryActionGovernancePolicyRepository;
import io.github.actionguard.core.repository.InMemoryActionInstanceRepository;
import io.github.actionguard.core.repository.InMemoryActionOutboxRepository;
import io.github.actionguard.core.repository.InMemoryActionStepInstanceRepository;
import io.github.actionguard.core.repository.InMemoryActionTransitionLogRepository;
import io.github.actionguard.store.mysql.MysqlActionCompensationLogRepository;
import io.github.actionguard.store.mysql.MysqlActionConsumeLogRepository;
import io.github.actionguard.store.mysql.MysqlActionGovernancePolicyRepository;
import io.github.actionguard.store.mysql.MysqlActionGuardStoreAutoConfiguration;
import io.github.actionguard.store.mysql.MysqlActionInstanceRepository;
import io.github.actionguard.store.mysql.MysqlActionOutboxRepository;
import io.github.actionguard.store.mysql.MysqlActionStepInstanceRepository;
import io.github.actionguard.store.mysql.MysqlActionTransitionLogRepository;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionGuardStoreSelectionTest {

    private final ApplicationContextRunner starter = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ActionGuardAutoConfiguration.class));

    private final ApplicationContextRunner database = starter
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                    DataSourceTransactionManagerAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
                    MybatisAutoConfiguration.class, MysqlActionGuardStoreAutoConfiguration.class))
            .withPropertyValues("spring.datasource.url=jdbc:h2:mem:store_selection;MODE=MySQL",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.datasource.username=sa", "spring.datasource.password=");

    @Test
    void shouldRequireExplicitSelection() {
        database.run(context -> assertThat(context.getStartupFailure())
                .hasMessageContaining("必须显式配置 action.guard.store.type"));
    }

    @Test
    void shouldRejectUnsupportedAndEmptySelection() {
        for (String type : new String[]{"redis", "", "mysqll"}) {
            database.withPropertyValues("action.guard.store.type=" + type)
                    .run(context -> assertThat(context.getStartupFailure())
                            .hasMessageContaining("支持 memory 或 mysql"));
        }
    }

    @Test
    void shouldSelectAllMemoryRepositoriesEvenWithDatabaseModuleAndDataSource() {
        database.withPropertyValues("action.guard.store.type=memory").run(context -> {
            assertThat(context).hasNotFailed();
            Map<Class<?>, Class<?>> expected = Map.of(
                    ActionInstanceRepository.class, InMemoryActionInstanceRepository.class,
                    ActionStepInstanceRepository.class, InMemoryActionStepInstanceRepository.class,
                    ActionOutboxRepository.class, InMemoryActionOutboxRepository.class,
                    ActionConsumeLogRepository.class, InMemoryActionConsumeLogRepository.class,
                    ActionGovernancePolicyRepository.class, InMemoryActionGovernancePolicyRepository.class,
                    ActionCompensationLogRepository.class, InMemoryActionCompensationLogRepository.class,
                    ActionTransitionLogRepository.class, InMemoryActionTransitionLogRepository.class);
            expected.forEach((contract, implementation) -> {
                assertThat(context.getBeansOfType(contract)).hasSize(1);
                assertThat(context.getBean(contract)).isInstanceOf(implementation);
            });
            assertThat(context).doesNotHaveBean(MysqlActionGuardStoreAutoConfiguration.class);
        });
    }

    @Test
    void shouldSelectAllMysqlRepositories() {
        database.withPropertyValues("action.guard.store.type=mysql").run(context -> {
            assertThat(context).hasNotFailed();
            Map<Class<?>, Class<?>> expected = Map.of(
                    ActionInstanceRepository.class, MysqlActionInstanceRepository.class,
                    ActionStepInstanceRepository.class, MysqlActionStepInstanceRepository.class,
                    ActionOutboxRepository.class, MysqlActionOutboxRepository.class,
                    ActionConsumeLogRepository.class, MysqlActionConsumeLogRepository.class,
                    ActionGovernancePolicyRepository.class, MysqlActionGovernancePolicyRepository.class,
                    ActionCompensationLogRepository.class, MysqlActionCompensationLogRepository.class,
                    ActionTransitionLogRepository.class, MysqlActionTransitionLogRepository.class);
            expected.forEach((contract, implementation) -> {
                assertThat(context.getBeansOfType(contract)).hasSize(1);
                assertThat(context.getBean(contract)).isInstanceOf(implementation);
            });
            assertThat(context).doesNotHaveBean(InMemoryActionGuardStoreConfiguration.class);
        });
    }

    @Test
    void shouldRunMemoryWithoutMysqlModule() {
        starter.withClassLoader(new FilteredClassLoader("io.github.actionguard.store.mysql"))
                .withPropertyValues("action.guard.store.type=memory")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void shouldRejectMysqlWithoutModule() {
        starter.withClassLoader(new FilteredClassLoader("io.github.actionguard.store.mysql"))
                .withPropertyValues("action.guard.store.type=mysql")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasMessageContaining("需要引入 action-guard-store-mysql 模块"));
    }

    @Test
    void shouldRejectMysqlWithoutDataSource() {
        starter.withConfiguration(AutoConfigurations.of(MysqlActionGuardStoreAutoConfiguration.class))
                .withPropertyValues("action.guard.store.type=mysql")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasMessageContaining("需要配置 DataSource 和数据库驱动"));
    }

    @Test
    void shouldRejectMysqlWithMissingDriver() {
        database.withPropertyValues("action.guard.store.type=mysql",
                        "spring.datasource.driver-class-name=missing.Driver")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("Cannot load driver class: missing.Driver"));
    }
}
