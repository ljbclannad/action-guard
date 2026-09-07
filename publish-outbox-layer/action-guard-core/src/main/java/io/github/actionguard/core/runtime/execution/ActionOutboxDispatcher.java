package io.github.actionguard.core.runtime.execution;

import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/** 单条 Outbox 的投递入口，统一抢占、发送、完成及失败回退。 */
public class ActionOutboxDispatcher {

    private final ActionOutboxRepository repository;
    private final Optional<ActionExecutionMessageProducer> producer;
    private final ActionObservabilityService observability;
    private final Clock clock;

    public ActionOutboxDispatcher(
            ActionOutboxRepository repository,
            Optional<ActionExecutionMessageProducer> producer,
            ActionObservabilityService observability,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.producer = Objects.requireNonNull(producer, "producer must not be null");
        this.observability = Objects.requireNonNull(observability, "observability must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** CLAIMED 候选只能由恢复扫描在确认租约超时后传入。 */
    public boolean dispatch(ActionOutbox candidate, int maxAttempts) {
        if (producer.isEmpty() || (candidate.status() != ActionOutboxStatus.NEW
                && candidate.status() != ActionOutboxStatus.CLAIMED)
                || candidate.availableAt().isAfter(clock.instant()) || maxAttempts <= 0) {
            return false;
        }
        ActionOutbox current = candidate;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            ActionOutbox claimed;
            try {
                claimed = save(current, ActionOutboxStatus.CLAIMED, current.attemptCount());
            } catch (OptimisticLockingFailureException ex) {
                return false;
            }
            try {
                producer.orElseThrow().publish(claimed);
            } catch (RuntimeException ex) {
                try {
                    current = save(claimed, ActionOutboxStatus.NEW, claimed.attemptCount() + 1);
                } catch (OptimisticLockingFailureException conflict) {
                    return false;
                }
                if (attempt == maxAttempts) {
                    observability.outboxPublishFailed(current, current.attemptCount(), ex.getMessage());
                }
                continue;
            }
            // 发送成功后的落库冲突表示状态已被其他执行者推进，不能再回退或立即重发。
            try {
                save(claimed, ActionOutboxStatus.DONE, claimed.attemptCount());
                return true;
            } catch (OptimisticLockingFailureException ex) {
                return false;
            }
        }
        return false;
    }

    private ActionOutbox save(ActionOutbox outbox, ActionOutboxStatus status, int attemptCount) {
        return repository.save(new ActionOutbox(
                outbox.id(), outbox.actionInstanceId(), outbox.topic(), outbox.dispatchId(),
                status, outbox.availableAt(), attemptCount, outbox.version(),
                outbox.createdAt(), clock.instant()
        ));
    }
}
