package io.github.actionguard.ops.api.service;

import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.model.ActionStepStatus;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.repository.ActionStepInstanceRepository;
import io.github.actionguard.core.repository.ActionTransitionLogRepository;
import io.github.actionguard.core.runtime.compensation.ActionCompensationExecutor;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import io.github.actionguard.core.runtime.state.ActionTransitionContext;
import io.github.actionguard.core.runtime.state.ActionTransitionExecution;
import io.github.actionguard.core.runtime.state.ActionTransitionEvent;
import io.github.actionguard.core.runtime.state.ActionTransitionMetadata;
import io.github.actionguard.core.runtime.state.ActionTransitionService;
import io.github.actionguard.ops.api.support.ActionCommandValidator;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ActionCommandService {

    private final ActionInstanceRepository actionInstanceRepository;
    private final ActionOutboxRepository actionOutboxRepository;
    private final ActionStepInstanceRepository actionStepInstanceRepository;
    private final ActionCommandValidator validator;
    private final ActionAuditService auditService;
    private final Optional<ActionExecutionMessageProducer> producer;
    private final ActionCompensationExecutor actionCompensationExecutor;
    private final ActionObservabilityService actionObservabilityService;
    private final ActionTransitionLogRepository actionTransitionLogRepository;
    private final ActionTransitionService actionTransitionService;

    public ActionCommandService(
            ActionInstanceRepository actionInstanceRepository,
            ActionOutboxRepository actionOutboxRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionCommandValidator validator,
            ActionAuditService auditService,
            Optional<ActionExecutionMessageProducer> producer,
            ActionCompensationExecutor actionCompensationExecutor,
            ActionObservabilityService actionObservabilityService
    ) {
        this(
                actionInstanceRepository,
                actionOutboxRepository,
                actionStepInstanceRepository,
                validator,
                auditService,
                producer,
                actionCompensationExecutor,
                actionObservabilityService,
                new io.github.actionguard.core.repository.InMemoryActionTransitionLogRepository()
        );
    }

    public ActionCommandService(
            ActionInstanceRepository actionInstanceRepository,
            ActionOutboxRepository actionOutboxRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionCommandValidator validator,
            ActionAuditService auditService,
            Optional<ActionExecutionMessageProducer> producer,
            ActionCompensationExecutor actionCompensationExecutor,
            ActionObservabilityService actionObservabilityService,
            ActionTransitionLogRepository actionTransitionLogRepository
    ) {
        this.actionInstanceRepository = Objects.requireNonNull(actionInstanceRepository, "actionInstanceRepository must not be null");
        this.actionOutboxRepository = Objects.requireNonNull(actionOutboxRepository, "actionOutboxRepository must not be null");
        this.actionStepInstanceRepository = Objects.requireNonNull(actionStepInstanceRepository, "actionStepInstanceRepository must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
        this.producer = Objects.requireNonNull(producer, "producer must not be null");
        this.actionCompensationExecutor = Objects.requireNonNull(actionCompensationExecutor, "actionCompensationExecutor must not be null");
        this.actionObservabilityService = Objects.requireNonNull(actionObservabilityService, "actionObservabilityService must not be null");
        this.actionTransitionLogRepository = Objects.requireNonNull(actionTransitionLogRepository, "actionTransitionLogRepository must not be null");
        this.actionTransitionService = new ActionTransitionService(
                this.actionInstanceRepository,
                this.actionTransitionLogRepository,
                this.actionObservabilityService
        );
    }

    public ActionCommandService(
            ActionInstanceRepository actionInstanceRepository,
            ActionOutboxRepository actionOutboxRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionCommandValidator validator,
            ActionAuditService auditService,
            Optional<ActionExecutionMessageProducer> producer,
            ActionCompensationExecutor actionCompensationExecutor
    ) {
        this(
                actionInstanceRepository,
                actionOutboxRepository,
                actionStepInstanceRepository,
                validator,
                auditService,
                producer,
                actionCompensationExecutor,
                new ActionObservabilityService(Optional.empty(), Optional.empty(), java.time.Clock.systemUTC()),
                new io.github.actionguard.core.repository.InMemoryActionTransitionLogRepository()
        );
    }

    @Transactional
    public void retry(String actionInstanceId, String operator) {
        try {
            ActionInstance actionInstance = actionInstanceRepository.findById(actionInstanceId)
                    .orElseThrow(() -> new IllegalArgumentException("Action not found: " + actionInstanceId));
            ActionOutbox outbox = actionOutboxRepository.findByActionInstanceId(actionInstanceId)
                    .orElseThrow(() -> new IllegalStateException("Outbox not found for action: " + actionInstanceId));
            if (actionInstance.status() == ActionStatus.RETRYING
                    && (outbox.status() == io.github.actionguard.core.model.ActionOutboxStatus.NEW
                    || outbox.status() == io.github.actionguard.core.model.ActionOutboxStatus.CLAIMED)) {
                auditService.record(actionInstanceId, "RETRY", operator, "{}", "SUCCESS", "retry already scheduled");
                actionObservabilityService.governanceCommand("RETRY", "SUCCESS");
                return;
            }
            validator.validateRetry(actionInstance.status());
            publishOutboxAfterCommit(outbox);
            auditService.record(actionInstanceId, "RETRY", operator, "{}", "SUCCESS", "retry dispatched");
            actionObservabilityService.governanceCommand("RETRY", "SUCCESS");
        } catch (RuntimeException ex) {
            auditService.record(actionInstanceId, "RETRY", operator, "{}", "FAILED", ex.getMessage());
            actionObservabilityService.governanceCommand("RETRY", "FAILED");
            throw ex;
        }
    }

    @Transactional
    public void cancel(String actionInstanceId, String operator) {
        try {
            ActionInstance actionInstance = actionInstanceRepository.findById(actionInstanceId)
                    .orElseThrow(() -> new IllegalArgumentException("Action not found: " + actionInstanceId));
            if (actionInstance.status() == ActionStatus.IGNORED) {
                auditService.record(actionInstanceId, "CANCEL", operator, "{}", "SUCCESS", "action already ignored");
                actionObservabilityService.governanceCommand("CANCEL", "SUCCESS");
                return;
            }
            validator.validateCancel(actionInstance.status());
            ActionTransitionExecution transitionExecution = actionTransitionService.transition(
                    actionInstance,
                    ActionTransitionEvent.MANUAL_CANCEL_REQUESTED,
                    ActionTransitionContext.of(
                            actionInstance.currentStepIndex(),
                            actionInstance.lastErrorCode(),
                            actionInstance.lastErrorMessage(),
                            Instant.now()
                    ),
                    ActionTransitionMetadata.of(
                            actionInstance.currentStepIndex(),
                            null,
                            null,
                            operator,
                            null,
                            null
                    )
            );
            auditService.recordTransition(
                    actionInstanceId,
                    "CANCEL",
                    operator,
                    transitionExecution.transitionResult(),
                    "SUCCESS",
                    "action ignored"
            );
            actionObservabilityService.governanceCommand("CANCEL", "SUCCESS");
        } catch (RuntimeException ex) {
            auditService.record(actionInstanceId, "CANCEL", operator, "{}", "FAILED", ex.getMessage());
            actionObservabilityService.governanceCommand("CANCEL", "FAILED");
            throw ex;
        }
    }

    @Transactional
    public void skip(String actionInstanceId, String operator) {
        try {
            ActionInstance actionInstance = actionInstanceRepository.findById(actionInstanceId)
                    .orElseThrow(() -> new IllegalArgumentException("Action not found: " + actionInstanceId));
            validator.validateSkip(actionInstance.status());
            List<ActionStepInstance> steps = actionStepInstanceRepository.findByActionInstanceId(actionInstanceId);
            if (actionInstance.currentStepIndex() >= steps.size()) {
                throw new IllegalStateException("No current step to skip for action: " + actionInstanceId);
            }
            ActionStepInstance currentStep = steps.get(actionInstance.currentStepIndex());
            actionStepInstanceRepository.save(new ActionStepInstance(
                    currentStep.id(),
                    currentStep.actionInstanceId(),
                    currentStep.stepIndex(),
                    currentStep.stepName(),
                    currentStep.stepType(),
                    currentStep.target(),
                    ActionStepStatus.SUCCESS,
                    currentStep.attemptCount(),
                    currentStep.payload(),
                    currentStep.lastErrorCode(),
                    currentStep.lastErrorMessage(),
                    currentStep.version(),
                    currentStep.createdAt(),
                    Instant.now()
            ));
            int nextStepIndex = currentStep.stepIndex() + 1;
            ActionTransitionContext transitionContext = ActionTransitionContext.of(
                    nextStepIndex,
                    actionInstance.lastErrorCode(),
                    actionInstance.lastErrorMessage(),
                    Instant.now()
            );
            ActionTransitionExecution transitionExecution = actionTransitionService.transition(
                    actionInstance,
                    ActionTransitionEvent.MANUAL_SKIP_REQUESTED,
                    transitionContext,
                    ActionTransitionMetadata.of(
                            currentStep.stepIndex(),
                            currentStep.stepName(),
                            currentStep.stepType(),
                            operator,
                            null,
                            null
                    )
            );
            ActionStatus nextStatus = transitionExecution.transitionResult().actionInstance().status();
            if (nextStatus == ActionStatus.DISPATCHING) {
                scheduleNextStep(actionInstanceId);
            }
            auditService.recordTransition(
                    actionInstanceId,
                    "SKIP",
                    operator,
                    transitionExecution.transitionResult(),
                    "SUCCESS",
                    "current step skipped"
            );
            actionObservabilityService.governanceCommand("SKIP", "SUCCESS");
        } catch (RuntimeException ex) {
            auditService.record(actionInstanceId, "SKIP", operator, "{}", "FAILED", ex.getMessage());
            actionObservabilityService.governanceCommand("SKIP", "FAILED");
            throw ex;
        }
    }

    @Transactional
    public void compensate(String actionInstanceId, String operator) {
        ActionInstance actionInstance = actionInstanceRepository.findById(actionInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Action not found: " + actionInstanceId));
        if (actionInstance.status() == ActionStatus.COMPENSATING) {
            auditService.record(actionInstanceId, "COMPENSATE", operator, "{}", "SUCCESS", "compensation already in progress");
            actionObservabilityService.governanceCommand("COMPENSATE", "SUCCESS");
            return;
        }
        if (actionInstance.status() == ActionStatus.COMPENSATED) {
            auditService.record(actionInstanceId, "COMPENSATE", operator, "{}", "SUCCESS", "compensation already completed");
            actionObservabilityService.governanceCommand("COMPENSATE", "SUCCESS");
            return;
        }
        validator.validateCompensate(actionInstance.status());
        try {
            actionCompensationExecutor.compensate(actionInstanceId);
            auditService.record(actionInstanceId, "COMPENSATE", operator, "{}", "SUCCESS", "compensation completed");
            actionObservabilityService.governanceCommand("COMPENSATE", "SUCCESS");
        } catch (RuntimeException ex) {
            auditService.record(actionInstanceId, "COMPENSATE", operator, "{}", "FAILED", ex.getMessage());
            actionObservabilityService.governanceCommand("COMPENSATE", "FAILED");
            throw ex;
        }
    }

    private void scheduleNextStep(String actionInstanceId) {
        ActionOutbox outbox = actionOutboxRepository.findByActionInstanceId(actionInstanceId)
                .orElseThrow(() -> new IllegalStateException("Outbox not found for action: " + actionInstanceId));
        Instant now = Instant.now();
        ActionOutbox scheduledOutbox = actionOutboxRepository.save(new ActionOutbox(
                outbox.id(),
                outbox.actionInstanceId(),
                outbox.topic(),
                io.github.actionguard.core.model.ActionOutboxStatus.NEW,
                now,
                outbox.attemptCount(),
                outbox.version(),
                outbox.createdAt(),
                now
        ));
        publishOutboxAfterCommit(scheduledOutbox);
    }

    private void publishOutboxAfterCommit(ActionOutbox outbox) {
        ActionExecutionMessageProducer requiredProducer = producer
                .orElseThrow(() -> new IllegalStateException("ActionExecutionMessageProducer is not available"));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            requiredProducer.publish(outbox);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                requiredProducer.publish(outbox);
            }
        });
    }
}
