package io.github.actionguard.core.runtime.execution;

import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 单条 Outbox 的投递入口，统一抢占、发送、完成及失败回退。 */
public class ActionOutboxDispatcher {

    private final ActionOutboxRepository repository;
    private final ActionExecutionMessageProducer producer;
    private final ActionObservabilityService observability;
    private final Clock clock;
    private final int maxDeliveryAttempts;
    private final Duration retryBackoff;

    /**
     * 保留 Optional 作为兼容构造器入口；可选依赖会在构造阶段立即解包，不进入运行时状态机。
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public ActionOutboxDispatcher(
            ActionOutboxRepository repository,
            Optional<ActionExecutionMessageProducer> producer,
            ActionObservabilityService observability,
            Clock clock
    ) {
        this(repository, producer, observability, clock, 10, Duration.ofSeconds(5));
    }

    /**
     * 保留 Optional 作为兼容构造器入口；可选依赖会在构造阶段立即解包，不进入运行时状态机。
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public ActionOutboxDispatcher(
            ActionOutboxRepository repository,
            Optional<ActionExecutionMessageProducer> producer,
            ActionObservabilityService observability,
            Clock clock,
            int maxDeliveryAttempts,
            Duration retryBackoff
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.producer = Objects.requireNonNull(producer, "producer must not be null").orElse(null);
        this.observability = Objects.requireNonNull(observability, "observability must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.maxDeliveryAttempts = Math.max(1, maxDeliveryAttempts);
        this.retryBackoff = Objects.requireNonNull(retryBackoff, "retryBackoff must not be null").isNegative()
                ? Duration.ZERO
                : retryBackoff;
    }

    /** CLAIMED 候选只能由恢复扫描在确认租约超时后传入。 */
    public boolean dispatch(ActionOutbox candidate, int maxAttempts) {
        // 仅处理已到期、可投递的 NEW 记录，或由恢复扫描接管的超时 CLAIMED 记录。
        if (producer == null || (candidate.status() != ActionOutboxStatus.NEW
                && candidate.status() != ActionOutboxStatus.CLAIMED)
                || candidate.availableAt().isAfter(clock.instant()) || maxAttempts <= 0) {
            return false;
        }
        // 每次失败回退后使用最新持久化快照继续尝试，避免沿用已过期的 version。
        ActionOutbox current = candidate;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            ActionOutbox claimed;
            try {
                // 先以乐观锁抢占为 CLAIMED；抢占失败说明其他执行者已更新，当前执行者直接退出。
                claimed = save(current, ActionOutboxStatus.CLAIMED, current.availableAt(), current.attemptCount(),
                        current.deliveryAttemptCount());
            } catch (OptimisticLockingFailureException ex) {
                return false;
            }
            try {
                // 只有抢占成功的快照才允许发送，避免多个节点同时投递同一条 Outbox。
                producer.publish(claimed);
            } catch (RuntimeException ex) {
                int deliveryAttemptCount = claimed.deliveryAttemptCount() + 1;
                try {
                    if (deliveryAttemptCount >= maxDeliveryAttempts) {
                        current = save(claimed, ActionOutboxStatus.DEAD, claimed.availableAt(),
                                claimed.attemptCount() + 1, deliveryAttemptCount);
                        observability.outboxDead(current, maxDeliveryAttempts, ex.getMessage());
                        return false;
                    }
                    // 当前调用内的同步重试保持可立即派发；耗尽后再退避，避免恢复扫描持续冲击故障通道。
                    boolean retryInCurrentCall = attempt < maxAttempts;
                    current = save(claimed, ActionOutboxStatus.NEW,
                            retryInCurrentCall ? claimed.availableAt() : clock.instant().plus(retryBackoff),
                            claimed.attemptCount() + 1, deliveryAttemptCount);
                } catch (OptimisticLockingFailureException conflict) {
                    return false;
                }
                if (attempt == maxAttempts) {
                    observability.outboxPublishFailed(current, current.deliveryAttemptCount(), ex.getMessage());
                }
                continue;
            }
            // Broker 已确认发送后再落库为 DONE；这里仍存在 MQ 与数据库非原子窗口。
            // 落库冲突表示状态已被其他执行者推进，不能再回退或立即重发。
            try {
                save(claimed, ActionOutboxStatus.DONE, claimed.availableAt(), claimed.attemptCount(),
                        claimed.deliveryAttemptCount());
                return true;
            } catch (OptimisticLockingFailureException ex) {
                return false;
            }
        }
        return false;
    }

    private ActionOutbox save(
            ActionOutbox outbox,
            ActionOutboxStatus status,
            Instant availableAt,
            int attemptCount,
            int deliveryAttemptCount
    ) {
        return repository.save(new ActionOutbox(
                outbox.id(), outbox.actionInstanceId(), outbox.topic(), outbox.dispatchId(),
                status, availableAt, attemptCount, deliveryAttemptCount, outbox.version(),
                outbox.createdAt(), clock.instant()
        ));
    }
}
