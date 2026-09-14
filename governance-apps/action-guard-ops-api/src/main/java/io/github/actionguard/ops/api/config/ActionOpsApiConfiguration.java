package io.github.actionguard.ops.api.config;

import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.repository.ActionStepInstanceRepository;
import io.github.actionguard.core.repository.ActionTransitionLogRepository;
import io.github.actionguard.core.runtime.compensation.ActionCompensationExecutor;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import io.github.actionguard.ops.api.repository.*;
import io.github.actionguard.ops.api.repository.jdbc.*;
import io.github.actionguard.ops.api.security.ActionOpsAuthorizationInterceptor;
import io.github.actionguard.ops.api.service.ActionAuditService;
import io.github.actionguard.ops.api.service.ActionCommandService;
import io.github.actionguard.ops.api.service.ActionQueryService;
import io.github.actionguard.ops.api.support.ActionCommandValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Optional;

@Configuration(proxyBeanMethods = false)
public class ActionOpsApiConfiguration {

    @Bean
    ActionAuditLogRepository actionAuditLogRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcActionAuditLogRepository(jdbcTemplate);
    }

    @Bean
    ActionOpsQueryRepository actionOpsQueryRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcActionOpsQueryRepository(jdbcTemplate);
    }

    @Bean
    ActionOutboxQueryRepository actionOutboxQueryRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcActionOutboxQueryRepository(jdbcTemplate);
    }

    @Bean
    ActionAlertOutboxQueryRepository actionAlertOutboxQueryRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcActionAlertOutboxQueryRepository(jdbcTemplate);
    }

    @Bean
    ActionCompensationLogQueryRepository actionCompensationLogQueryRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcActionCompensationLogQueryRepository(jdbcTemplate);
    }

    @Bean
    ActionAuditService actionAuditService(ActionAuditLogRepository actionAuditLogRepository) {
        return new ActionAuditService(actionAuditLogRepository);
    }

    @Bean
    ActionQueryService actionQueryService(
            ActionOpsQueryRepository actionOpsQueryRepository,
            ActionOutboxQueryRepository actionOutboxQueryRepository,
            ActionAlertOutboxQueryRepository actionAlertOutboxQueryRepository,
            ActionCompensationLogQueryRepository actionCompensationLogQueryRepository,
            ActionTransitionLogRepository actionTransitionLogRepository
    ) {
        return new ActionQueryService(
                actionOpsQueryRepository,
                actionOutboxQueryRepository,
                actionAlertOutboxQueryRepository,
                actionCompensationLogQueryRepository,
                actionTransitionLogRepository
        );
    }

    @Bean
    ActionCommandValidator actionCommandValidator() {
        return new ActionCommandValidator();
    }

    @Bean
    ActionOpsAuthorizationInterceptor actionOpsAuthorizationInterceptor(
            ObjectProvider<io.github.actionguard.ops.api.security.ActionOpsPrincipalResolver> principalResolverProvider
    ) {
        return new ActionOpsAuthorizationInterceptor(principalResolverProvider);
    }

    @Bean
    WebMvcConfigurer actionOpsWebMvcConfigurer(ActionOpsAuthorizationInterceptor actionOpsAuthorizationInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(actionOpsAuthorizationInterceptor)
                        .addPathPatterns("/api/actions/**", "/api/audit-logs/**");
            }
        };
    }

    @Bean
    ActionCommandService actionCommandService(
            ActionInstanceRepository actionInstanceRepository,
            ActionOutboxRepository actionOutboxRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionCommandValidator actionCommandValidator,
            ActionAuditService actionAuditService,
            Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer,
            ActionCompensationExecutor actionCompensationExecutor,
            ActionObservabilityService actionObservabilityService,
            ActionTransitionLogRepository actionTransitionLogRepository
    ) {
        return new ActionCommandService(
                actionInstanceRepository,
                actionOutboxRepository,
                actionStepInstanceRepository,
                actionCommandValidator,
                actionAuditService,
                actionExecutionMessageProducer,
                actionCompensationExecutor,
                actionObservabilityService,
                actionTransitionLogRepository
        );
    }
}
