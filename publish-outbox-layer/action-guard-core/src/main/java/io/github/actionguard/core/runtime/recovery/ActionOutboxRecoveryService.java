package io.github.actionguard.core.runtime.recovery;

import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ActionOutboxRecoveryService {

    private final ActionOutboxRepository actionOutboxRepository;
    private final Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer;
    private final ActionObservabilityService actionObservabilityService;
    private final Clock clock;

    public ActionOutboxRecoveryService(
            ActionOutboxRepository actionOutboxRepository,
            Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer,
            ActionObservabilityService actionObservabilityService,
            Clock clock
    ) {
        this.actionOutboxRepository = Objects.requireNonNull(actionOutboxRepository, "actionOutboxRepository must not be null");
        this.actionExecutionMessageProducer = Objects.requireNonNull(actionExecutionMessageProducer, "actionExecutionMessageProducer must not be null");
        this.actionObservabilityService = Objects.requireNonNull(actionObservabilityService, "actionObservabilityService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public int recoverDueOutboxes(int batchSize, Duration claimTimeout) {
        if (batchSize <= 0 || actionExecutionMessageProducer.isEmpty()) {
            return 0;
        }
        Instant now = clock.instant();
        List<ActionOutbox> candidates = actionOutboxRepository.findRecoverable(now, now.minus(claimTimeout), batchSize);
        int recoveredCount = 0;
        for (ActionOutbox candidate : candidates) {
            if (tryRecover(candidate)) {
                recoveredCount++;
            }
        }
        return recoveredCount;
    }

    private boolean tryRecover(ActionOutbox candidate) {
        ActionOutbox claimed;
        try {
            claimed = actionOutboxRepository.save(new ActionOutbox(
                    candidate.id(),
                    candidate.actionInstanceId(),
                    candidate.topic(),
                    ActionOutboxStatus.CLAIMED,
                    candidate.availableAt(),
                    candidate.attemptCount(),
                    candidate.version(),
                    candidate.createdAt(),
                    clock.instant()
            ));
        } catch (OptimisticLockingFailureException ex) {
            return false;
        }
        try {
            actionExecutionMessageProducer.orElseThrow().publish(claimed);
            actionOutboxRepository.save(new ActionOutbox(
                    claimed.id(),
                    claimed.actionInstanceId(),
                    claimed.topic(),
                    ActionOutboxStatus.DONE,
                    claimed.availableAt(),
                    claimed.attemptCount(),
                    claimed.version(),
                    claimed.createdAt(),
                    clock.instant()
            ));
            return true;
        } catch (RuntimeException ex) {
            ActionOutbox reset = actionOutboxRepository.save(new ActionOutbox(
                    claimed.id(),
                    claimed.actionInstanceId(),
                    claimed.topic(),
                    ActionOutboxStatus.NEW,
                    claimed.availableAt(),
                    claimed.attemptCount(),
                    claimed.version(),
                    claimed.createdAt(),
                    clock.instant()
            ));
            actionObservabilityService.outboxPublishFailed(reset, reset.attemptCount(), ex.getMessage());
            return false;
        }
    }
}
