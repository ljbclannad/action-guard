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
        publishImmediately(scheduledOutbox);
    }

    private void publishImmediately(ActionOutbox outbox) {
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
