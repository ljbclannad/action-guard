package io.github.actionguard.core.runtime;

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

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class ActionCompensationService implements ActionCompensationExecutor {

    private final ActionInstanceRepository actionInstanceRepository;
    private final ActionStepInstanceRepository actionStepInstanceRepository;
    private final ActionDefinitionRegistry actionDefinitionRegistry;
    private final ActionGovernancePolicyRepository actionGovernancePolicyRepository;
    private final ActionCompensationLogRepository actionCompensationLogRepository;
    private final ActionCompensatorRegistry actionCompensatorRegistry;
    private final Clock clock;

    public ActionCompensationService(
            ActionInstanceRepository actionInstanceRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionDefinitionRegistry actionDefinitionRegistry,
            ActionGovernancePolicyRepository actionGovernancePolicyRepository,
            ActionCompensationLogRepository actionCompensationLogRepository,
            ActionCompensatorRegistry actionCompensatorRegistry,
            Clock clock
    ) {
        this.actionInstanceRepository = Objects.requireNonNull(actionInstanceRepository, "actionInstanceRepository must not be null");
        this.actionStepInstanceRepository = Objects.requireNonNull(actionStepInstanceRepository, "actionStepInstanceRepository must not be null");
        this.actionDefinitionRegistry = Objects.requireNonNull(actionDefinitionRegistry, "actionDefinitionRegistry must not be null");
        this.actionGovernancePolicyRepository = Objects.requireNonNull(actionGovernancePolicyRepository, "actionGovernancePolicyRepository must not be null");
        this.actionCompensationLogRepository = Objects.requireNonNull(actionCompensationLogRepository, "actionCompensationLogRepository must not be null");
        this.actionCompensatorRegistry = Objects.requireNonNull(actionCompensatorRegistry, "actionCompensatorRegistry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void compensate(String actionInstanceId) {
        ActionInstance actionInstance = actionInstanceRepository.findById(actionInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Action not found: " + actionInstanceId));
        if (actionInstance.status() != ActionStatus.FAILED && actionInstance.status() != ActionStatus.DEAD) {
            throw new IllegalStateException("Compensation is not allowed for status: " + actionInstance.status());
        }
        if (!effectiveCompensationEnabled(actionInstance.actionName())) {
            throw new IllegalStateException("compensation is disabled for action: " + actionInstance.actionName());
        }

        Instant now = clock.instant();
        actionInstance = actionInstanceRepository.save(new ActionInstance(
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

        List<ActionStepInstance> successfulSteps = actionStepInstanceRepository.findByActionInstanceId(actionInstanceId).stream()
                .filter(step -> step.status() == ActionStepStatus.SUCCESS)
                .sorted(Comparator.comparingInt(ActionStepInstance::stepIndex).reversed())
                .toList();
        String compensationBatchId = "batch-" + actionInstanceId;

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
