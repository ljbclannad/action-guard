package io.github.actionguard.core.runtime.recovery;

import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import io.github.actionguard.core.runtime.execution.ActionOutboxDispatcher;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;

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
    private final ActionOutboxDispatcher outboxDispatcher;
    private final Clock clock;

    public ActionOutboxRecoveryService(
            ActionOutboxRepository actionOutboxRepository,
            Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer,
            ActionObservabilityService actionObservabilityService,
            Clock clock
    ) {
        this.actionOutboxRepository = Objects.requireNonNull(actionOutboxRepository, "actionOutboxRepository must not be null");
        this.actionExecutionMessageProducer = Objects.requireNonNull(actionExecutionMessageProducer, "actionExecutionMessageProducer must not be null");
        this.outboxDispatcher = new ActionOutboxDispatcher(actionOutboxRepository, actionExecutionMessageProducer, actionObservabilityService, clock);
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
            if (outboxDispatcher.dispatch(candidate, 1)) {
                recoveredCount++;
            }
        }
        return recoveredCount;
    }
}
