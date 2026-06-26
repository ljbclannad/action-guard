package io.github.actionguard.core.runtime;

import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.api.runtime.ActionRetryAction;
import io.github.actionguard.api.runtime.ActionRetryContext;
import io.github.actionguard.api.runtime.ActionStepContext;
import io.github.actionguard.api.runtime.StepExecutionResult;
import io.github.actionguard.api.spi.ActionRetryPolicy;
import io.github.actionguard.api.spi.ActionStepHandler;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.model.ActionStepStatus;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.repository.ActionStepInstanceRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class DefaultActionExecutionCallback implements ActionExecutionCallback {

    private final ActionInstanceRepository actionInstanceRepository;
    private final ActionStepInstanceRepository actionStepInstanceRepository;
    private final StepHandlerRegistry stepHandlerRegistry;
    private final ActionRetryPolicy actionRetryPolicy;
    private final ActionOutboxRepository actionOutboxRepository;
    private final Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer;
    private final Clock clock;

    public DefaultActionExecutionCallback(
            ActionInstanceRepository actionInstanceRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            StepHandlerRegistry stepHandlerRegistry,
            ActionRetryPolicy actionRetryPolicy,
            ActionOutboxRepository actionOutboxRepository,
            Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer,
            Clock clock
    ) {
        this.actionInstanceRepository = Objects.requireNonNull(actionInstanceRepository, "actionInstanceRepository must not be null");
        this.actionStepInstanceRepository = Objects.requireNonNull(actionStepInstanceRepository, "actionStepInstanceRepository must not be null");
        this.stepHandlerRegistry = Objects.requireNonNull(stepHandlerRegistry, "stepHandlerRegistry must not be null");
        this.actionRetryPolicy = Objects.requireNonNull(actionRetryPolicy, "actionRetryPolicy must not be null");
        this.actionOutboxRepository = Objects.requireNonNull(actionOutboxRepository, "actionOutboxRepository must not be null");
        this.actionExecutionMessageProducer = Objects.requireNonNull(actionExecutionMessageProducer, "actionExecutionMessageProducer must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void execute(ActionExecutionMessage message) {
        ActionInstance actionInstance = actionInstanceRepository.findById(message.actionInstanceId())
                .orElseThrow(() -> new IllegalArgumentException("ActionInstance not found: " + message.actionInstanceId()));
        List<ActionStepInstance> stepInstances = actionStepInstanceRepository.findByActionInstanceId(message.actionInstanceId());
        if (actionInstance.currentStepIndex() >= stepInstances.size()) {
            throw new IllegalStateException("No executable step for actionInstanceId: " + message.actionInstanceId());
        }

        ActionStepInstance currentStep = stepInstances.get(actionInstance.currentStepIndex());
        ActionStepHandler handler = stepHandlerRegistry.getRequired(currentStep.stepType());
        StepExecutionResult result = handler.execute(new ActionStepContext(
                actionInstance.actionName(),
                actionInstance.bizKey(),
                currentStep.stepName(),
                currentStep.stepType(),
                currentStep.target(),
                actionInstance.attributes(),
                currentStep.payload()
        ));

        if (result.success()) {
            handleStepSuccess(actionInstance, currentStep);
            return;
        }
        handleStepFailure(actionInstance, currentStep, result);
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
        if (nextStatus == ActionStatus.DISPATCHING) {
            dispatchNextStep(advanced, now);
        }
    }

    private void handleStepFailure(ActionInstance actionInstance, ActionStepInstance currentStep, StepExecutionResult result) {
        Instant now = clock.instant();
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
                normalizedErrorMessage(result),
                currentStep.version(),
                currentStep.createdAt(),
                now
        ));
        ActionRetryAction retryAction = actionRetryPolicy.decide(
                new StepExecutionException(result.errorCode(), normalizedErrorMessage(result)),
                new ActionRetryContext(failedStep.attemptCount(), Integer.MAX_VALUE, true)
        );
        if (retryAction == ActionRetryAction.IMMEDIATE_RETRY) {
            ActionInstance retrying = actionInstanceRepository.save(new ActionInstance(
                    actionInstance.id(),
                    actionInstance.actionName(),
                    actionInstance.bizKey(),
                    ActionStatus.RETRYING,
                    currentStep.stepIndex(),
                    actionInstance.totalStepCount(),
                    actionInstance.attributes(),
                    result.errorCode(),
                    normalizedErrorMessage(result),
                    actionInstance.version(),
                    actionInstance.createdAt(),
                    now
            ));
            dispatchRetry(retrying, now);
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
                normalizedErrorMessage(result),
                actionInstance.version(),
                actionInstance.createdAt(),
                now
        ));
    }

    private String normalizedErrorMessage(StepExecutionResult result) {
        return result.errorMessage() == null || result.errorMessage().isBlank()
                ? "step execution failed"
                : result.errorMessage();
    }

    private void dispatchNextStep(ActionInstance actionInstance, Instant now) {
        if (actionExecutionMessageProducer.isEmpty()) {
            return;
        }
        ActionOutbox outbox = actionOutboxRepository.findByActionInstanceId(actionInstance.id())
                .orElseThrow(() -> new IllegalStateException("Outbox not found for actionInstanceId: " + actionInstance.id()));
        ActionOutbox updatedOutbox = actionOutboxRepository.save(new ActionOutbox(
                outbox.id(),
                outbox.actionInstanceId(),
                outbox.topic(),
                ActionOutboxStatus.NEW,
                now,
                outbox.attemptCount(),
                outbox.version(),
                outbox.createdAt(),
                now
        ));
        actionExecutionMessageProducer.orElseThrow().publish(updatedOutbox);
    }

    private void dispatchRetry(ActionInstance actionInstance, Instant now) {
        if (actionExecutionMessageProducer.isEmpty()) {
            return;
        }
        ActionOutbox outbox = actionOutboxRepository.findByActionInstanceId(actionInstance.id())
                .orElseThrow(() -> new IllegalStateException("Outbox not found for actionInstanceId: " + actionInstance.id()));
        ActionOutbox updatedOutbox = actionOutboxRepository.save(new ActionOutbox(
                outbox.id(),
                outbox.actionInstanceId(),
                outbox.topic(),
                ActionOutboxStatus.NEW,
                now,
                outbox.attemptCount() + 1,
                outbox.version(),
                outbox.createdAt(),
                now
        ));
        actionExecutionMessageProducer.orElseThrow().publish(updatedOutbox);
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
