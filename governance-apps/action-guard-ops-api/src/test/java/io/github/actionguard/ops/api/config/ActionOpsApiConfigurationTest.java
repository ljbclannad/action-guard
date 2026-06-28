package io.github.actionguard.ops.api.config;

import io.github.actionguard.core.repository.ActionGovernancePolicyRepository;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.repository.ActionStepInstanceRepository;
import io.github.actionguard.core.runtime.compensation.ActionCompensationExecutor;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import io.github.actionguard.ops.api.service.ActionAuditService;
import io.github.actionguard.ops.api.service.ActionCommandService;
import io.github.actionguard.ops.api.service.ActionQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ActionOpsApiConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withConfiguration(AutoConfigurations.of(ActionOpsApiConfiguration.class));

    @Test
    void shouldWireOpsApiBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ActionAuditService.class);
            assertThat(context).hasSingleBean(ActionQueryService.class);
            assertThat(context).hasSingleBean(ActionCommandService.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:ops_api_config;MODE=MySQL;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
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
            jdbcTemplate.execute("""
                    create table if not exists action_instance (
                        id varchar(64) primary key,
                        action_name varchar(128) not null,
                        biz_key varchar(256) not null,
                        status varchar(32) not null,
                        current_step_index int not null,
                        total_step_count int not null,
                        attributes_json text,
                        last_error_code varchar(128),
                        last_error_message text,
                        version int not null default 0,
                        created_at timestamp not null,
                        updated_at timestamp not null
                    )
                    """);
            jdbcTemplate.execute("""
                    create table if not exists action_step_instance (
                        id varchar(64) primary key,
                        action_instance_id varchar(64) not null,
                        step_index int not null,
                        step_name varchar(128) not null,
                        step_type varchar(128) not null,
                        target varchar(256) not null,
                        status varchar(32) not null,
                        attempt_count int not null,
                        payload_json text,
                        last_error_code varchar(128),
                        last_error_message text,
                        version int not null default 0,
                        created_at timestamp not null,
                        updated_at timestamp not null
                    )
                    """);
            jdbcTemplate.execute("""
                    create table if not exists action_consume_log (
                        id varchar(64) primary key,
                        message_id varchar(128) not null,
                        action_instance_id varchar(64) not null,
                        consumer_group varchar(128) not null,
                        consume_status varchar(32) not null,
                        dedupe_key varchar(128) not null,
                        attempt_count int not null,
                        last_error_message text,
                        version int not null default 0,
                        first_received_at timestamp not null,
                        last_received_at timestamp not null,
                        updated_at timestamp not null
                    )
                    """);
            return jdbcTemplate;
        }

        @Bean
        ActionInstanceRepository actionInstanceRepository() {
            return org.mockito.Mockito.mock(ActionInstanceRepository.class);
        }

        @Bean
        ActionOutboxRepository actionOutboxRepository() {
            return org.mockito.Mockito.mock(ActionOutboxRepository.class);
        }

        @Bean
        ActionStepInstanceRepository actionStepInstanceRepository() {
            return org.mockito.Mockito.mock(ActionStepInstanceRepository.class);
        }

        @Bean
        ActionCompensationExecutor actionCompensationExecutor() {
            return actionInstanceId -> {
            };
        }

        @Bean
        ActionExecutionMessageProducer actionExecutionMessageProducer() {
            return outbox -> {
            };
        }

        @Bean
        ActionObservabilityService actionObservabilityService() {
            return new ActionObservabilityService(Optional.empty(), Optional.empty(), Clock.systemUTC());
        }
    }
}
