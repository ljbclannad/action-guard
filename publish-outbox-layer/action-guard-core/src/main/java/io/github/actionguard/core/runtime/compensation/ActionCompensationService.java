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
import io.github.actionguard.core.model.ActionTransitionLog;
import io.github.actionguard.core.repository.ActionCompensationLogRepository;
import io.github.actionguard.core.repository.ActionGovernancePolicyRepository;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.InMemoryActionTransitionLogRepository;
import io.github.actionguard.core.repository.ActionStepInstanceRepository;
import io.github.actionguard.core.repository.ActionTransitionLogRepository;
import io.github.actionguard.core.runtime.definition.ActionDefinitionRegistry;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import io.github.actionguard.core.runtime.state.ActionCommand;
import io.github.actionguard.core.runtime.state.ActionStateMachine;
import io.github.actionguard.core.runtime.state.ActionTransitionContext;
import io.github.actionguard.core.runtime.state.ActionTransitionExecution;
import io.github.actionguard.core.runtime.state.ActionTransitionEvent;
import io.github.actionguard.core.runtime.state.ActionTransitionMetadata;
import io.github.actionguard.core.runtime.state.ActionTransitionService;

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
    private final ActionTransitionLogRepository actionTransitionLogRepository;
    private final ActionCompensatorRegistry actionCompensatorRegistry;
    private final ActionObservabilityService actionObservabilityService;
    private final ActionTransitionService actionTransitionService;
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
        this(
                actionInstanceRepository,
                actionStepInstanceRepository,
                actionDefinitionRegistry,
                actionGovernancePolicyRepository,
                actionCompensationLogRepository,
                new InMemoryActionTransitionLogRepository(),
                actionCompensatorRegistry,
                actionObservabilityService,
                clock
        );
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
                new InMemoryActionTransitionLogRepository(),
                actionCompensatorRegistry,
                new ActionObservabilityService(Optional.empty(), Optional.empty(), clock),
                clock
        );
    }

    public ActionCompensationService(
            ActionInstanceRepository actionInstanceRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionDefinitionRegistry actionDefinitionRegistry,
            ActionGovernancePolicyRepository actionGovernancePolicyRepository,
            ActionCompensationLogRepository actionCompensationLogRepository,
            ActionTransitionLogRepository actionTransitionLogRepository,
            ActionCompensatorRegistry actionCompensatorRegistry,
            ActionObservabilityService actionObservabilityService,
            Clock clock
    ) {
        this.actionInstanceRepository = Objects.requireNonNull(actionInstanceRepository, "actionInstanceRepository must not be null");
        this.actionStepInstanceRepository = Objects.requireNonNull(actionStepInstanceRepository, "actionStepInstanceRepository must not be null");
        this.actionDefinitionRegistry = Objects.requireNonNull(actionDefinitionRegistry, "actionDefinitionRegistry must not be null");
        this.actionGovernancePolicyRepository = Objects.requireNonNull(actionGovernancePolicyRepository, "actionGovernancePolicyRepository must not be null");
        this.actionCompensationLogRepository = Objects.requireNonNull(actionCompensationLogRepository, "actionCompensationLogRepository must not be null");
        this.actionTransitionLogRepository = Objects.requireNonNull(actionTransitionLogRepository, "actionTransitionLogRepository must not be null");
        this.actionCompensatorRegistry = Objects.requireNonNull(actionCompensatorRegistry, "actionCompensatorRegistry must not be null");
        this.actionObservabilityService = Objects.requireNonNull(actionObservabilityService, "actionObservabilityService must not be null");
        this.actionTransitionService = new ActionTransitionService(
                this.actionInstanceRepository,
                this.actionTransitionLogRepository,
                this.actionObservabilityService
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public ActionCompensationService(
            ActionInstanceRepository actionInstanceRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionDefinitionRegistry actionDefinitionRegistry,
            ActionGovernancePolicyRepository actionGovernancePolicyRepository,
            ActionCompensationLogRepository actionCompensationLogRepository,
            ActionTransitionLogRepository actionTransitionLogRepository,
            ActionCompensatorRegistry actionCompensatorRegistry,
            Clock clock
    ) {
        this(
                actionInstanceRepository,
                actionStepInstanceRepository,
                actionDefinitionRegistry,
                actionGovernancePolicyRepository,
                actionCompensationLogRepository,
                actionTransitionLogRepository,
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
        ActionStateMachine.assertCommandAllowed(actionInstance.status(), ActionCommand.COMPENSATE);
        if (!effectiveCompensationEnabled(actionInstance.actionName())) {
            throw new IllegalStateException("compensation is disabled for action: " + actionInstance.actionName());
        }

        // 人工或自动触发补偿时，先把动作抢占到 COMPENSATING，避免多个节点同时补偿同一条 action。
        executeCompensation(claimCompensating(actionInstance), false);
    }

    public int recoverInterruptedCompensations(int batchSize, Duration staleTimeout) {
        if (batchSize <= 0) {
            return 0;
        }
        Instant threshold = clock.instant().minus(staleTimeout);
        // 这里只接管“已经在补偿中，但长时间没有推进”的动作，避免误伤正常执行中的动作。
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
        return actionTransitionService.transition(
                actionInstance,
                ActionTransitionEvent.COMPENSATION_STARTED,
                ActionTransitionContext.atCurrentStep(actionInstance.currentStepIndex(), now),
                ActionTransitionMetadata.of(actionInstance.currentStepIndex(), null, null, null, null, null)
        ).transitionResult().actionInstance();
    }

    private void executeCompensation(ActionInstance actionInstance, boolean recoveryMode) {
        Set<String> completedStepIds = completedStepIds(actionInstance.id(), recoveryMode);
        List<ActionStepInstance> successfulSteps = actionStepInstanceRepository.findByActionInstanceId(actionInstance.id()).stream()
                .filter(step -> step.status() == ActionStepStatus.SUCCESS)
                .filter(step -> !completedStepIds.contains(step.id()))
                .sorted(Comparator.comparingInt(ActionStepInstance::stepIndex).reversed())
                .toList();
        String compensationBatchId = compensationBatchId(actionInstance.id());

        // 补偿严格按“成功步骤倒序”执行，尽量符合副作用回滚的自然顺序。
        for (ActionStepInstance step : successfulSteps) {
            ActionCompensator compensator = actionCompensatorRegistry.find(step.stepType()).orElse(null);
            if (compensator == null) {
                // 没有 compensator 时不阻断整条补偿链，而是记一条 SKIPPED 审计，方便后续人工判断。
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
                // 一旦某个补偿步骤失败，当前动作回到 DEAD，等待人工介入，而不是继续补偿剩余步骤。
                writeCompensationLog(compensationBatchId, step, "FAILED", compensator.getClass().getName(), result.message());
                actionObservabilityService.compensationFailed(actionInstance, step, result.message());
                actionTransitionService.transition(
                        actionInstance,
                        ActionTransitionEvent.COMPENSATION_FAILED,
                        ActionTransitionContext.failure(
                                actionInstance.currentStepIndex(),
                                "COMPENSATION_FAILED",
                                result.message(),
                                clock.instant()
                        ),
                        ActionTransitionMetadata.of(
                                step.stepIndex(),
                                step.stepName(),
                                step.stepType(),
                                null,
                                "COMPENSATION_FAILED",
                                result.message()
                        )
                );
                return;
            }
            writeCompensationLog(compensationBatchId, step, "SUCCESS", compensator.getClass().getName(), result.message());
        }

        ActionTransitionExecution transitionExecution = actionTransitionService.transition(
                actionInstance,
                ActionTransitionEvent.COMPENSATION_SUCCEEDED,
                ActionTransitionContext.atCurrentStep(actionInstance.currentStepIndex(), clock.instant()),
                ActionTransitionMetadata.of(actionInstance.currentStepIndex(), null, null, null, null, null)
        );
        actionObservabilityService.actionCompensated(actionInstance);
    }

    private Set<String> completedStepIds(String actionInstanceId, boolean recoveryMode) {
        if (!recoveryMode) {
            return Set.of();
        }
        // 恢复补偿时只跳过已成功或已明确跳过的步骤，避免重放已经完成的补偿动作。
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
