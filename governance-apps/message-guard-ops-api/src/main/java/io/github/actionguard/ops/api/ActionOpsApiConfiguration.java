package io.github.actionguard.ops.api;

import io.github.actionguard.core.repository.ActionGovernancePolicyRepository;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.repository.ActionStepInstanceRepository;
import io.github.actionguard.core.runtime.ActionCompensationExecutor;
import io.github.actionguard.core.runtime.ActionExecutionMessageProducer;
import io.github.actionguard.ops.api.repository.ActionAuditLogRepository;
import io.github.actionguard.ops.api.repository.ActionCompensationLogQueryRepository;
import io.github.actionguard.ops.api.repository.ActionOpsQueryRepository;
import io.github.actionguard.ops.api.repository.jdbc.JdbcActionAuditLogRepository;
import io.github.actionguard.ops.api.repository.jdbc.JdbcActionCompensationLogQueryRepository;
import io.github.actionguard.ops.api.repository.jdbc.JdbcActionOpsQueryRepository;
import io.github.actionguard.ops.api.service.ActionAuditService;
import io.github.actionguard.ops.api.service.ActionCommandService;
import io.github.actionguard.ops.api.service.ActionQueryService;
import io.github.actionguard.ops.api.support.ActionCommandValidator;
import io.github.actionguard.ops.api.support.OperatorResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

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
            ActionCompensationLogQueryRepository actionCompensationLogQueryRepository
    ) {
        return new ActionQueryService(actionOpsQueryRepository, actionCompensationLogQueryRepository);
    }

    @Bean
    ActionCommandValidator actionCommandValidator() {
        return new ActionCommandValidator();
    }

    @Bean
    OperatorResolver operatorResolver() {
        return new OperatorResolver();
    }

    @Bean
    ActionCommandService actionCommandService(
            ActionInstanceRepository actionInstanceRepository,
            ActionOutboxRepository actionOutboxRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionCommandValidator actionCommandValidator,
            ActionAuditService actionAuditService,
            Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer,
            ActionCompensationExecutor actionCompensationExecutor
    ) {
        return new ActionCommandService(
                actionInstanceRepository,
                actionOutboxRepository,
                actionStepInstanceRepository,
                actionCommandValidator,
                actionAuditService,
                actionExecutionMessageProducer,
                actionCompensationExecutor
        );
    }
}
