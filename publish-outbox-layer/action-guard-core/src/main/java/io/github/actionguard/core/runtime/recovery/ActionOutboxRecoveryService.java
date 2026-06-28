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

/**
 * Outbox 恢复服务。
 *
 * <p>它处在“主执行链路之外的兜底恢复路径”上，负责定期扫描已经到期但尚未完成投递的 outbox：
 * 包括还处于 {@code NEW} 状态的记录，以及已经 {@code CLAIMED} 但长时间未完成的疑似中断记录。
 *
 * <p>恢复时仍然遵循和实时路径相同的 claim -> publish -> done 语义，保证多节点下只有一个发送者真正接手。
 * 因此它不是一套独立逻辑，而是对主链路可靠投递机制的补偿与兜底。
 */
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
        // recoverable 查询会同时覆盖两类情况：
        // 1. 原本就还没发出去的 NEW 记录
        // 2. 已经 CLAIMED 但长时间没有完成的疑似中断记录
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
            // recovery 和实时路径都通过 claim 来争抢同一条 outbox 的发送权，谁先成功谁负责继续发送。
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
            // recover 失败后不要保留 CLAIMED 状态，否则这条 outbox 会长期卡住，后续节点也无法再接手。
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
