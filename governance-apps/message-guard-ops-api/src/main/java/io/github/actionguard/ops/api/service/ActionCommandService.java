package io.github.actionguard.ops.api.service;

import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.model.ActionStepStatus;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.repository.ActionStepInstanceRepository;
import io.github.actionguard.core.runtime.ActionCompensationExecutor;
import io.github.actionguard.core.runtime.ActionExecutionMessageProducer;
import io.github.actionguard.ops.api.support.ActionCommandValidator;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class ActionCommandService {

    private final ActionInstanceRepository actionInstanceRepository;
    private final ActionOutboxRepository actionOutboxRepository;
    private final ActionStepInstanceRepository actionStepInstanceRepository;
    private final ActionCommandValidator validator;
    private final ActionAuditService auditService;
    private final Optional<ActionExecutionMessageProducer> producer;
    private final ActionCompensationExecutor actionCompensationExecutor;

    public ActionCommandService(
            ActionInstanceRepository actionInstanceRepository,
            ActionOutboxRepository actionOutboxRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionCommandValidator validator,
            ActionAuditService auditService,
            Optional<ActionExecutionMessageProducer> producer,
            ActionCompensationExecutor actionCompensationExecutor
    ) {
        this.actionInstanceRepository = actionInstanceRepository;
        this.actionOutboxRepository = actionOutboxRepository;
        this.actionStepInstanceRepository = actionStepInstanceRepository;
        this.validator = validator;
        this.auditService = auditService;
        this.producer = producer;
        this.actionCompensationExecutor = actionCompensationExecutor;
    }

    public void retry(String actionInstanceId, String operator) {
        try {
            ActionInstance actionInstance = actionInstanceRepository.findById(actionInstanceId)
                    .orElseThrow(() -> new IllegalArgumentException("Action not found: " + actionInstanceId));
            validator.validateRetry(actionInstance.status());
            ActionOutbox outbox = actionOutboxRepository.findByActionInstanceId(actionInstanceId)
                    .orElseThrow(() -> new IllegalStateException("Outbox not found for action: " + actionInstanceId));
            producer.orElseThrow(() -> new IllegalStateException("ActionExecutionMessageProducer is not available")).publish(outbox);
            auditService.record(actionInstanceId, "RETRY", operator, "{}", "SUCCESS", "retry dispatched");
        } catch (RuntimeException ex) {
            auditService.record(actionInstanceId, "RETRY", operator, "{}", "FAILED", ex.getMessage());
            throw ex;
        }
    }

    public void cancel(String actionInstanceId, String operator) {
        try {
            ActionInstance actionInstance = actionInstanceRepository.findById(actionInstanceId)
                    .orElseThrow(() -> new IllegalArgumentException("Action not found: " + actionInstanceId));
            validator.validateCancel(actionInstance.status());
            actionInstanceRepository.save(new ActionInstance(
                    actionInstance.id(),
                    actionInstance.actionName(),
                    actionInstance.bizKey(),
                    ActionStatus.IGNORED,
                    actionInstance.currentStepIndex(),
                    actionInstance.totalStepCount(),
                    actionInstance.attributes(),
                    actionInstance.lastErrorCode(),
                    actionInstance.lastErrorMessage(),
                    actionInstance.version(),
                    actionInstance.createdAt(),
                    Instant.now()
            ));
            auditService.record(actionInstanceId, "CANCEL", operator, "{}", "SUCCESS", "action ignored");
        } catch (RuntimeException ex) {
            auditService.record(actionInstanceId, "CANCEL", operator, "{}", "FAILED", ex.getMessage());
            throw ex;
        }
    }

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
            ActionStatus nextStatus = nextStepIndex >= actionInstance.totalStepCount() ? ActionStatus.SUCCESS : ActionStatus.DISPATCHING;
            actionInstanceRepository.save(new ActionInstance(
                    actionInstance.id(),
                    actionInstance.actionName(),
                    actionInstance.bizKey(),
                    nextStatus,
                    nextStepIndex,
                    actionInstance.totalStepCount(),
                    actionInstance.attributes(),
                    actionInstance.lastErrorCode(),
                    actionInstance.lastErrorMessage(),
                    actionInstance.version(),
                    actionInstance.createdAt(),
                    Instant.now()
            ));
            auditService.record(actionInstanceId, "SKIP", operator, "{}", "SUCCESS", "current step skipped");
        } catch (RuntimeException ex) {
            auditService.record(actionInstanceId, "SKIP", operator, "{}", "FAILED", ex.getMessage());
            throw ex;
        }
    }

    public void compensate(String actionInstanceId, String operator) {
        ActionInstance actionInstance = actionInstanceRepository.findById(actionInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Action not found: " + actionInstanceId));
        validator.validateCompensate(actionInstance.status());
        try {
            actionCompensationExecutor.compensate(actionInstanceId);
            auditService.record(actionInstanceId, "COMPENSATE", operator, "{}", "SUCCESS", "compensation completed");
        } catch (RuntimeException ex) {
            auditService.record(actionInstanceId, "COMPENSATE", operator, "{}", "FAILED", ex.getMessage());
            throw ex;
        }
    }
}
