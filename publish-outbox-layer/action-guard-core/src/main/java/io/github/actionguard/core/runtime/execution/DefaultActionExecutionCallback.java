package io.github.actionguard.core.runtime.execution;

import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.api.runtime.ActionRetryAction;
import io.github.actionguard.api.runtime.ActionRetryContext;
import io.github.actionguard.api.runtime.ActionStepContext;
import io.github.actionguard.api.runtime.StepExecutionResult;
import io.github.actionguard.api.spi.ActionRetryPolicy;
import io.github.actionguard.api.spi.ActionStepHandler;
import io.github.actionguard.api.definition.ActionStepDefinition;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.model.ActionStepStatus;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.repository.ActionStepInstanceRepository;
import io.github.actionguard.core.runtime.definition.ActionDefinitionRegistry;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import io.github.actionguard.core.runtime.registry.StepHandlerRegistry;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Action 执行回调的核心协调实现。
 *
 * <p>它处在 {@code consumer -> callback -> handler} 这段链路的中心位置：MQ consumer 拿到并完成基础校验后，
 * 会把 {@code ActionExecutionMessage} 交给这里；这里再根据当前 action / step 状态定位可执行步骤，
 * 查找对应的 {@link ActionStepHandler}，并真正触发业务 handler 执行。
 *
 * <p>handler 返回结果后，这个类继续负责推进 action 状态机，决定下一步是成功推进、立即重试、
 * 延迟重试还是失败终止，并在需要时复用 outbox 再次投递执行消息。所以它既是 step handler 的调用入口，
 * 也是整条执行链路的状态编排器。
 */
public class DefaultActionExecutionCallback implements ActionExecutionCallback {

    private final ActionInstanceRepository actionInstanceRepository;
    private final ActionStepInstanceRepository actionStepInstanceRepository;
    private final ActionDefinitionRegistry actionDefinitionRegistry;
    private final StepHandlerRegistry stepHandlerRegistry;
    private final ActionRetryPolicy actionRetryPolicy;
    private final ActionOutboxRepository actionOutboxRepository;
    private final Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer;
    private final ActionObservabilityService actionObservabilityService;
    private final Clock clock;

    public DefaultActionExecutionCallback(
            ActionInstanceRepository actionInstanceRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionDefinitionRegistry actionDefinitionRegistry,
            StepHandlerRegistry stepHandlerRegistry,
            ActionRetryPolicy actionRetryPolicy,
            ActionOutboxRepository actionOutboxRepository,
            Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer,
            ActionObservabilityService actionObservabilityService,
            Clock clock
    ) {
        this.actionInstanceRepository = Objects.requireNonNull(actionInstanceRepository, "actionInstanceRepository must not be null");
        this.actionStepInstanceRepository = Objects.requireNonNull(actionStepInstanceRepository, "actionStepInstanceRepository must not be null");
        this.actionDefinitionRegistry = Objects.requireNonNull(actionDefinitionRegistry, "actionDefinitionRegistry must not be null");
        this.stepHandlerRegistry = Objects.requireNonNull(stepHandlerRegistry, "stepHandlerRegistry must not be null");
        this.actionRetryPolicy = Objects.requireNonNull(actionRetryPolicy, "actionRetryPolicy must not be null");
        this.actionOutboxRepository = Objects.requireNonNull(actionOutboxRepository, "actionOutboxRepository must not be null");
        this.actionExecutionMessageProducer = Objects.requireNonNull(actionExecutionMessageProducer, "actionExecutionMessageProducer must not be null");
        this.actionObservabilityService = Objects.requireNonNull(actionObservabilityService, "actionObservabilityService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public DefaultActionExecutionCallback(
            ActionInstanceRepository actionInstanceRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionDefinitionRegistry actionDefinitionRegistry,
            StepHandlerRegistry stepHandlerRegistry,
            ActionRetryPolicy actionRetryPolicy,
            ActionOutboxRepository actionOutboxRepository,
            Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer,
            Clock clock
    ) {
        this(
                actionInstanceRepository,
                actionStepInstanceRepository,
                actionDefinitionRegistry,
                stepHandlerRegistry,
                actionRetryPolicy,
                actionOutboxRepository,
                actionExecutionMessageProducer,
                new ActionObservabilityService(Optional.empty(), Optional.empty(), clock),
                clock
        );
    }

    @Override
    public void execute(ActionExecutionMessage message) {
        ActionInstance actionInstance = actionInstanceRepository.findById(message.actionInstanceId())
                .orElseThrow(() -> new IllegalArgumentException("ActionInstance not found: " + message.actionInstanceId()));
        // 终态动作天然幂等，重复投递的执行消息在这里直接短路，避免重复推进状态。
        if (actionInstance.status().isTerminal()) {
            return;
        }
        List<ActionStepInstance> stepInstances = actionStepInstanceRepository.findByActionInstanceId(message.actionInstanceId());
        if (actionInstance.currentStepIndex() >= stepInstances.size()) {
            if (actionInstance.status().isTerminal()) {
                return;
            }
            throw new IllegalStateException("No executable step for actionInstanceId: " + message.actionInstanceId());
        }

        ActionStepInstance currentStep = stepInstances.get(actionInstance.currentStepIndex());
        // 当前 step 已成功时直接返回，防止消费重复消息时再次执行同一个 handler。
        if (currentStep.status() == ActionStepStatus.SUCCESS) {
            return;
        }
        ActionStepDefinition stepDefinition = resolveStepDefinition(actionInstance, currentStep);
        ActionStepHandler handler = stepHandlerRegistry.getRequired(currentStep.stepType());
        Instant startedAt = clock.instant();
        StepExecutionResult result = handler.execute(new ActionStepContext(
                actionInstance.actionName(),
                actionInstance.bizKey(),
                currentStep.stepName(),
                currentStep.stepType(),
                currentStep.target(),
                actionInstance.attributes(),
                currentStep.payload()
        ));
        Instant completedAt = clock.instant();
        StepExecutionResult effectiveResult = applyTimeoutIfExceeded(result, stepDefinition, startedAt, completedAt);

        if (effectiveResult.success()) {
            handleStepSuccess(actionInstance, currentStep);
            return;
        }
        handleStepFailure(actionInstance, currentStep, stepDefinition, effectiveResult);
    }

    private void handleStepSuccess(ActionInstance actionInstance, ActionStepInstance currentStep) {
        Instant now = clock.instant();
        // 先把 step 标记为成功，再推进 action 状态。
        // 这样即使后续推进下一步失败，治理侧也能明确看到当前 step 已经完成。
        actionStepInstanceRepository.save(new ActionStepInstance(
                currentStep.id(),
                currentStep.actionInstanceId(),
                currentStep.stepIndex(),
                currentStep.stepName(),
                currentStep.stepType(),
                currentStep.target(),
                ActionStepStatus.SUCCESS,
                currentStep.attemptCount() + 1,
                currentStep.payload(),
                null,
                null,
                currentStep.version(),
                currentStep.createdAt(),
                now
        ));
        actionObservabilityService.stepSucceeded(actionInstance, currentStep);

        int nextStepIndex = currentStep.stepIndex() + 1;
        ActionStatus nextStatus = nextStepIndex >= actionInstance.totalStepCount() ? ActionStatus.SUCCESS : ActionStatus.DISPATCHING;
        ActionInstance advanced = actionInstanceRepository.save(new ActionInstance(
                actionInstance.id(),
                actionInstance.actionName(),
                actionInstance.bizKey(),
                nextStatus,
                nextStepIndex,
                actionInstance.totalStepCount(),
                actionInstance.attributes(),
                null,
                null,
                actionInstance.version(),
                actionInstance.createdAt(),
                now
        ));
        if (nextStatus == ActionStatus.SUCCESS) {
            actionObservabilityService.actionSucceeded(advanced, currentStep);
        }
        if (nextStatus == ActionStatus.DISPATCHING) {
            // 只有在还有后续 step 时才继续投递下一条执行消息，第一版始终保持严格串行。
            dispatchNextStep(advanced, now);
        }
    }

    private void handleStepFailure(
            ActionInstance actionInstance,
            ActionStepInstance currentStep,
            ActionStepDefinition stepDefinition,
            StepExecutionResult result
    ) {
        Instant now = clock.instant();
        String errorMessage = normalizedErrorMessage(result);
        // 失败先落到 step 实例，后续 retry / fail-fast / compensate 的决策都基于这次持久化结果。
        ActionStepInstance failedStep = actionStepInstanceRepository.save(new ActionStepInstance(
                currentStep.id(),
                currentStep.actionInstanceId(),
                currentStep.stepIndex(),
                currentStep.stepName(),
                currentStep.stepType(),
                currentStep.target(),
                ActionStepStatus.FAILED,
                currentStep.attemptCount() + 1,
                currentStep.payload(),
                result.errorCode(),
                errorMessage,
                currentStep.version(),
                currentStep.createdAt(),
                now
        ));
        int maxRetryCount = stepDefinition.maxRetryCount() == null ? Integer.MAX_VALUE : stepDefinition.maxRetryCount();
        ActionRetryAction retryAction = actionRetryPolicy.decide(
                new StepExecutionException(result.errorCode(), errorMessage),
                new ActionRetryContext(failedStep.attemptCount(), maxRetryCount, true)
        );
        if ("STEP_TIMEOUT".equals(result.errorCode())) {
            actionObservabilityService.stepTimedOut(actionInstance, failedStep);
        }
        actionObservabilityService.stepFailed(actionInstance, failedStep, result.errorCode());
        if (retryAction == ActionRetryAction.IMMEDIATE_RETRY || retryAction == ActionRetryAction.DELAY_RETRY) {
            // retry 不会创建新的 action，而是把同一个 action 重新置为 RETRYING，并复用原 outbox 重新调度。
            ActionInstance retrying = actionInstanceRepository.save(new ActionInstance(
                    actionInstance.id(),
                    actionInstance.actionName(),
                    actionInstance.bizKey(),
                    ActionStatus.RETRYING,
                    currentStep.stepIndex(),
                    actionInstance.totalStepCount(),
                    actionInstance.attributes(),
                    result.errorCode(),
                    errorMessage,
                    actionInstance.version(),
                    actionInstance.createdAt(),
                    now
            ));
            dispatchRetry(retrying, now.plusMillis(resolvedBackoffMillis(stepDefinition, retryAction)));
            return;
        }
        // 走到这里说明当前策略已经放弃继续执行，action 进入 FAILED，等待人工治理或补偿链路接管。
        actionInstanceRepository.save(new ActionInstance(
                actionInstance.id(),
                actionInstance.actionName(),
                actionInstance.bizKey(),
                ActionStatus.FAILED,
                currentStep.stepIndex(),
                actionInstance.totalStepCount(),
                actionInstance.attributes(),
                result.errorCode(),
                errorMessage,
                actionInstance.version(),
                actionInstance.createdAt(),
                now
        ));
        actionObservabilityService.actionFailed(actionInstance, failedStep, result.errorCode());
        actionObservabilityService.retryExhausted(actionInstance, failedStep, result.errorCode(), errorMessage);
    }

    private String normalizedErrorMessage(StepExecutionResult result) {
        return result.errorMessage() == null || result.errorMessage().isBlank()
                ? "step execution failed"
                : result.errorMessage();
    }

    private void dispatchNextStep(ActionInstance actionInstance, Instant now) {
        dispatchOutbox(actionInstance.id(), now, false);
    }

    private void dispatchRetry(ActionInstance actionInstance, Instant now) {
        dispatchOutbox(actionInstance.id(), now, true);
    }

    private void dispatchOutbox(String actionInstanceId, Instant availableAt, boolean incrementAttemptCount) {
        ActionOutbox outbox = actionOutboxRepository.findByActionInstanceId(actionInstanceId)
                .orElseThrow(() -> new IllegalStateException("Outbox not found for actionInstanceId: " + actionInstanceId));
        // 下一步执行和重试都复用同一条 outbox，只更新可执行时间和尝试次数，避免额外制造重复消息。
        ActionOutbox scheduledOutbox = actionOutboxRepository.save(new ActionOutbox(
                outbox.id(),
                outbox.actionInstanceId(),
                outbox.topic(),
                ActionOutboxStatus.NEW,
                availableAt,
                incrementAttemptCount ? outbox.attemptCount() + 1 : outbox.attemptCount(),
                outbox.version(),
                outbox.createdAt(),
                clock.instant()
        ));
        if (actionExecutionMessageProducer.isEmpty() || availableAt.isAfter(clock.instant())) {
            return;
        }
        // 只有“立即可执行”的消息才在当前线程直接尝试投递；延迟重试交给 recovery/scheduler 再次扫描。
        publishImmediately(scheduledOutbox);
    }

    private void publishImmediately(ActionOutbox outbox) {
        // 先把 outbox claim 成 CLAIMED，再真正 publish，避免多个节点同时把同一条消息重复发到 MQ。
        ActionOutbox claimedOutbox = actionOutboxRepository.save(new ActionOutbox(
                outbox.id(),
                outbox.actionInstanceId(),
                outbox.topic(),
                ActionOutboxStatus.CLAIMED,
                outbox.availableAt(),
                outbox.attemptCount(),
                outbox.version(),
                outbox.createdAt(),
                clock.instant()
        ));
        try {
            actionExecutionMessageProducer.orElseThrow().publish(claimedOutbox);
            actionOutboxRepository.save(new ActionOutbox(
                    claimedOutbox.id(),
                    claimedOutbox.actionInstanceId(),
                    claimedOutbox.topic(),
                    ActionOutboxStatus.DONE,
                    claimedOutbox.availableAt(),
                    claimedOutbox.attemptCount(),
                    claimedOutbox.version(),
                    claimedOutbox.createdAt(),
                    clock.instant()
            ));
        } catch (RuntimeException ex) {
            // 发送失败时把 outbox 还原回 NEW，交给后续恢复任务重试，而不是在这里静默丢失。
            ActionOutbox reset = actionOutboxRepository.save(new ActionOutbox(
                    claimedOutbox.id(),
                    claimedOutbox.actionInstanceId(),
                    claimedOutbox.topic(),
                    ActionOutboxStatus.NEW,
                    claimedOutbox.availableAt(),
                    claimedOutbox.attemptCount(),
                    claimedOutbox.version(),
                    claimedOutbox.createdAt(),
                    clock.instant()
            ));
            actionObservabilityService.outboxPublishFailed(reset, reset.attemptCount(), ex.getMessage());
        }
    }

    private ActionStepDefinition resolveStepDefinition(ActionInstance actionInstance, ActionStepInstance currentStep) {
        ActionStepDefinition stepDefinition = actionDefinitionRegistry.getRequired(actionInstance.actionName())
                .steps()
                .get(currentStep.stepIndex());
        if (!stepDefinition.stepType().equals(currentStep.stepType())) {
            throw new IllegalStateException("Action definition step type mismatch: " + actionInstance.actionName() + "/" + currentStep.stepName());
        }
        return stepDefinition;
    }

    private StepExecutionResult applyTimeoutIfExceeded(
            StepExecutionResult result,
            ActionStepDefinition stepDefinition,
            Instant startedAt,
            Instant completedAt
    ) {
        if (result == null) {
            throw new IllegalStateException("step execution result must not be null");
        }
        if (stepDefinition.timeoutMillis() == null) {
            return result;
        }
        long elapsedMillis = Math.max(0, completedAt.toEpochMilli() - startedAt.toEpochMilli());
        if (elapsedMillis <= stepDefinition.timeoutMillis()) {
            return result;
        }
        return StepExecutionResult.failed(
                "STEP_TIMEOUT",
                "step execution timed out after " + elapsedMillis + " ms"
        );
    }

    private long resolvedBackoffMillis(ActionStepDefinition stepDefinition, ActionRetryAction retryAction) {
        if (retryAction == ActionRetryAction.DELAY_RETRY) {
            return stepDefinition.retryBackoffMillis() == null ? 0L : stepDefinition.retryBackoffMillis();
        }
        return stepDefinition.retryBackoffMillis() == null ? 0L : stepDefinition.retryBackoffMillis();
    }

    private static final class StepExecutionException extends RuntimeException {
        private final String errorCode;

        private StepExecutionException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        @SuppressWarnings("unused")
        private String errorCode() {
            return errorCode;
        }
    }
}
