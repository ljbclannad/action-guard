package io.github.actionguard.starter;

import io.github.actionguard.api.ActionPublisher;
import io.github.actionguard.api.ActionRequest;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.runtime.ActionExecutionMessageProducer;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class TransactionalActionPublisher implements ActionPublisher {

    private final ActionPublisher delegate;
    private final ActionInstanceRepository actionInstanceRepository;
    private final ActionOutboxRepository actionOutboxRepository;
    private final Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer;
    private final int publishRetryMaxAttempts;

    public TransactionalActionPublisher(
            ActionPublisher delegate,
            ActionInstanceRepository actionInstanceRepository,
            ActionOutboxRepository actionOutboxRepository,
            Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer,
            int publishRetryMaxAttempts
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.actionInstanceRepository = Objects.requireNonNull(actionInstanceRepository, "actionInstanceRepository must not be null");
        this.actionOutboxRepository = Objects.requireNonNull(actionOutboxRepository, "actionOutboxRepository must not be null");
        this.actionExecutionMessageProducer = Objects.requireNonNull(actionExecutionMessageProducer, "actionExecutionMessageProducer must not be null");
        this.publishRetryMaxAttempts = Math.max(1, publishRetryMaxAttempts);
    }

    @Override
    @Transactional
    public void publish(ActionRequest request) {
        delegate.publish(request);
        if (actionExecutionMessageProducer.isEmpty()) {
            return;
        }
        ActionOutbox outbox = resolveOutbox(request);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishAfterCommit(outbox);
                }
            });
            return;
        }
        publishAfterCommit(outbox);
    }

    private ActionOutbox resolveOutbox(ActionRequest request) {
        ActionInstance actionInstance = actionInstanceRepository
                .findByActionNameAndBizKey(request.actionName(), request.bizKey())
                .orElseThrow(() -> new IllegalStateException("Published action instance not found"));
        return actionOutboxRepository.findByActionInstanceId(actionInstance.id())
                .orElseThrow(() -> new IllegalStateException("Outbox not found for action instance"));
    }

    private void publishAfterCommit(ActionOutbox outbox) {
        ActionOutbox currentOutbox = outbox;
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= publishRetryMaxAttempts; attempt++) {
            try {
                actionExecutionMessageProducer.orElseThrow().publish(currentOutbox);
                markOutbox(currentOutbox, ActionOutboxStatus.DONE, currentOutbox.attemptCount());
                return;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                int nextAttemptCount = attempt;
                ActionOutboxStatus nextStatus = attempt >= publishRetryMaxAttempts ? ActionOutboxStatus.DEAD : ActionOutboxStatus.NEW;
                currentOutbox = markOutbox(currentOutbox, nextStatus, nextAttemptCount);
            }
        }
        throw Objects.requireNonNull(lastFailure, "lastFailure must not be null");
    }

    private ActionOutbox markOutbox(ActionOutbox outbox, ActionOutboxStatus status, int attemptCount) {
        Instant now = Instant.now();
        return actionOutboxRepository.save(new ActionOutbox(
                outbox.id(),
                outbox.actionInstanceId(),
                outbox.topic(),
                status,
                outbox.availableAt(),
                attemptCount,
                outbox.version(),
                outbox.createdAt(),
                now
        ));
    }
}
