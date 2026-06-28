package io.github.actionguard.core.runtime.compensation;

import io.github.actionguard.api.definition.ActionDefinition;
import io.github.actionguard.api.runtime.ActionCompensationContext;
import io.github.actionguard.api.runtime.ActionCompensationResult;
import io.github.actionguard.api.spi.ActionCompensator;
import io.github.actionguard.core.model.ActionCompensationLog;
import io.github.actionguard.core.model.ActionGovernancePolicy;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.model.ActionStepStatus;
import io.github.actionguard.core.repository.ActionCompensationLogRepository;
import io.github.actionguard.core.repository.ActionGovernancePolicyRepository;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionStepInstanceRepository;
import io.github.actionguard.core.runtime.definition.ActionDefinitionRegistry;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ActionCompensationService implements ActionCompensationExecutor {

    private final ActionInstanceRepository actionInstanceRepository;
    private final ActionStepInstanceRepository actionStepInstanceRepository;
    private final ActionDefinitionRegistry actionDefinitionRegistry;
    private final ActionGovernancePolicyRepository actionGovernancePolicyRepository;
    private final ActionCompensationLogRepository actionCompensationLogRepository;
    private final ActionCompensatorRegistry actionCompensatorRegistry;
    private final ActionObservabilityService actionObservabilityService;
    private final Clock clock;

    public ActionCompensationService(
            ActionInstanceRepository actionInstanceRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionDefinitionRegistry actionDefinitionRegistry,
            ActionGovernancePolicyRepository actionGovernancePolicyRepository,
            ActionCompensationLogRepository actionCompensationLogRepository,
            ActionCompensatorRegistry actionCompensatorRegistry,
            ActionObservabilityService actionObservabilityService,
            Clock clock
    ) {
        this.actionInstanceRepository = Objects.requireNonNull(actionInstanceRepository, "actionInstanceRepository must not be null");
        this.actionStepInstanceRepository = Objects.requireNonNull(actionStepInstanceRepository, "actionStepInstanceRepository must not be null");
        this.actionDefinitionRegistry = Objects.requireNonNull(actionDefinitionRegistry, "actionDefinitionRegistry must not be null");
        this.actionGovernancePolicyRepository = Objects.requireNonNull(actionGovernancePolicyRepository, "actionGovernancePolicyRepository must not be null");
        this.actionCompensationLogRepository = Objects.requireNonNull(actionCompensationLogRepository, "actionCompensationLogRepository must not be null");
        this.actionCompensatorRegistry = Objects.requireNonNull(actionCompensatorRegistry, "actionCompensatorRegistry must not be null");
        this.actionObservabilityService = Objects.requireNonNull(actionObservabilityService, "actionObservabilityService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public ActionCompensationService(
            ActionInstanceRepository actionInstanceRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionDefinitionRegistry actionDefinitionRegistry,
            ActionGovernancePolicyRepository actionGovernancePolicyRepository,
            ActionCompensationLogRepository actionCompensationLogRepository,
            ActionCompensatorRegistry actionCompensatorRegistry,
            Clock clock
    ) {
        this(
                actionInstanceRepository,
                actionStepInstanceRepository,
                actionDefinitionRegistry,
                actionGovernancePolicyRepository,
                actionCompensationLogRepository,
                actionCompensatorRegistry,
                new ActionObservabilityService(Optional.empty(), Optional.empty(), clock),
                clock
        );
    }

    public void compensate(String actionInstanceId) {
        ActionInstance actionInstance = actionInstanceRepository.findById(actionInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Action not found: " + actionInstanceId));
        if (actionInstance.status() == ActionStatus.COMPENSATED || actionInstance.status() == ActionStatus.COMPENSATING) {
            return;
        }
        if (actionInstance.status() != ActionStatus.FAILED && actionInstance.status() != ActionStatus.DEAD) {
            throw new IllegalStateException("Compensation is not allowed for status: " + actionInstance.status());
        }
        if (!effectiveCompensationEnabled(actionInstance.actionName())) {
            throw new IllegalStateException("compensation is disabled for action: " + actionInstance.actionName());
        }

        executeCompensation(claimCompensating(actionInstance), false);
    }

    public int recoverInterruptedCompensations(int batchSize, Duration staleTimeout) {
        if (batchSize <= 0) {
            return 0;
        }
        Instant threshold = clock.instant().minus(staleTimeout);
        List<ActionInstance> candidates = actionInstanceRepository.findByStatusesAndUpdatedBefore(
                List.of(ActionStatus.COMPENSATING),
                threshold,
                batchSize
        );
        int recoveredCount = 0;
        for (ActionInstance candidate : candidates) {
            try {
                executeCompensation(claimCompensating(candidate), true);
                recoveredCount++;
            } catch (org.springframework.dao.OptimisticLockingFailureException ignored) {
                // Another node has already reclaimed this compensation task.
            }
        }
        return recoveredCount;
    }

    private ActionInstance claimCompensating(ActionInstance actionInstance) {
        Instant now = clock.instant();
        return actionInstanceRepository.save(new ActionInstance(
                actionInstance.id(),
                actionInstance.actionName(),
                actionInstance.bizKey(),
                ActionStatus.COMPENSATING,
                actionInstance.currentStepIndex(),
                actionInstance.totalStepCount(),
                actionInstance.attributes(),
                null,
                null,
                actionInstance.version(),
                actionInstance.createdAt(),
                now
        ));
    }

    private void executeCompensation(ActionInstance actionInstance, boolean recoveryMode) {
        Set<String> completedStepIds = completedStepIds(actionInstance.id(), recoveryMode);
        List<ActionStepInstance> successfulSteps = actionStepInstanceRepository.findByActionInstanceId(actionInstance.id()).stream()
                .filter(step -> step.status() == ActionStepStatus.SUCCESS)
                .filter(step -> !completedStepIds.contains(step.id()))
                .sorted(Comparator.comparingInt(ActionStepInstance::stepIndex).reversed())
                .toList();
        String compensationBatchId = compensationBatchId(actionInstance.id());

        for (ActionStepInstance step : successfulSteps) {
            ActionCompensator compensator = actionCompensatorRegistry.find(step.stepType()).orElse(null);
            if (compensator == null) {
                writeCompensationLog(compensationBatchId, step, "SKIPPED", null, "no compensator registered");
                continue;
            }
            ActionCompensationResult result = compensator.compensate(new ActionCompensationContext(
                    actionInstance.actionName(),
                    actionInstance.bizKey(),
                    step.stepName(),
                    step.stepType(),
                    step.payload()
            ));
            if (!result.success()) {
                writeCompensationLog(compensationBatchId, step, "FAILED", compensator.getClass().getName(), result.message());
                actionObservabilityService.compensationFailed(actionInstance, step, result.message());
                actionInstanceRepository.save(new ActionInstance(
                        actionInstance.id(),
                        actionInstance.actionName(),
                        actionInstance.bizKey(),
                        ActionStatus.DEAD,
                        actionInstance.currentStepIndex(),
                        actionInstance.totalStepCount(),
                        actionInstance.attributes(),
                        "COMPENSATION_FAILED",
                        result.message(),
                        actionInstance.version(),
                        actionInstance.createdAt(),
                        clock.instant()
                ));
                return;
            }
            writeCompensationLog(compensationBatchId, step, "SUCCESS", compensator.getClass().getName(), result.message());
        }

        actionInstanceRepository.save(new ActionInstance(
                actionInstance.id(),
                actionInstance.actionName(),
                actionInstance.bizKey(),
                ActionStatus.COMPENSATED,
                actionInstance.currentStepIndex(),
                actionInstance.totalStepCount(),
                actionInstance.attributes(),
                null,
                null,
                actionInstance.version(),
                actionInstance.createdAt(),
                clock.instant()
        ));
        actionObservabilityService.actionCompensated(actionInstance);
    }

    private Set<String> completedStepIds(String actionInstanceId, boolean recoveryMode) {
        if (!recoveryMode) {
            return Set.of();
        }
        return actionCompensationLogRepository.findByActionInstanceId(actionInstanceId).stream()
                .filter(log -> "SUCCESS".equals(log.compensationStatus()) || "SKIPPED".equals(log.compensationStatus()))
                .map(ActionCompensationLog::actionStepInstanceId)
                .collect(Collectors.toSet());
    }

    private String compensationBatchId(String actionInstanceId) {
        return "batch-" + actionInstanceId;
    }

    private boolean effectiveCompensationEnabled(String actionName) {
        ActionDefinition definition = actionDefinitionRegistry.getRequired(actionName);
        return actionGovernancePolicyRepository.findByActionName(actionName)
                .map(ActionGovernancePolicy::compensationEnabled)
                .orElse(definition.compensationEnabled());
    }

    private void writeCompensationLog(
            String compensationBatchId,
            ActionStepInstance step,
            String compensationStatus,
            String compensatorName,
            String resultMessage
    ) {
        Instant now = clock.instant();
        actionCompensationLogRepository.save(new ActionCompensationLog(
                java.util.UUID.randomUUID().toString(),
                compensationBatchId,
                step.actionInstanceId(),
                step.id(),
                step.stepIndex(),
                step.stepName(),
                step.stepType(),
                compensationStatus,
                compensatorName,
                resultMessage,
                now,
                now
        ));
    }
}
