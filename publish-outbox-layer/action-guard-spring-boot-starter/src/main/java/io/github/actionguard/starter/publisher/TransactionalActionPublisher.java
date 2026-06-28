package io.github.actionguard.starter.publisher;

import io.github.actionguard.api.ActionPublisher;
import io.github.actionguard.api.ActionRequest;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Starter 层对 {@link ActionPublisher} 的事务语义包装器。
 *
 * <p>它位于“core 可靠落库能力 -> Spring 事务与消息投递”的衔接边界上：内部委托
 * {@code DefaultActionPublisher} 先创建 action / step / outbox 记录，再根据当前事务状态决定
 * 何时把 outbox 真正投递到执行通道。
 *
 * <p>这个类存在的核心原因是避免脏消息。只要当前线程处于事务中，它就把 MQ 发送动作延后到
 * {@code afterCommit}，确保“数据库提交成功”与“开始异步执行”之间的顺序正确；如果提交后的即时投递失败，
 * 则只更新 outbox 状态，把后续恢复留给 recovery 链路处理。
 */
public class TransactionalActionPublisher implements ActionPublisher {

    private final ActionPublisher delegate;
    private final ActionInstanceRepository actionInstanceRepository;
    private final ActionOutboxRepository actionOutboxRepository;
    private final Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer;
    private final int publishRetryMaxAttempts;
    private final ActionObservabilityService actionObservabilityService;

    public TransactionalActionPublisher(
            ActionPublisher delegate,
            ActionInstanceRepository actionInstanceRepository,
            ActionOutboxRepository actionOutboxRepository,
            Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer,
            int publishRetryMaxAttempts,
            ActionObservabilityService actionObservabilityService
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.actionInstanceRepository = Objects.requireNonNull(actionInstanceRepository, "actionInstanceRepository must not be null");
        this.actionOutboxRepository = Objects.requireNonNull(actionOutboxRepository, "actionOutboxRepository must not be null");
        this.actionExecutionMessageProducer = Objects.requireNonNull(actionExecutionMessageProducer, "actionExecutionMessageProducer must not be null");
        this.publishRetryMaxAttempts = Math.max(1, publishRetryMaxAttempts);
        this.actionObservabilityService = Objects.requireNonNull(actionObservabilityService, "actionObservabilityService must not be null");
    }

    public TransactionalActionPublisher(
            ActionPublisher delegate,
            ActionInstanceRepository actionInstanceRepository,
            ActionOutboxRepository actionOutboxRepository,
            Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer,
            int publishRetryMaxAttempts
    ) {
        this(
                delegate,
                actionInstanceRepository,
                actionOutboxRepository,
                actionExecutionMessageProducer,
                publishRetryMaxAttempts,
                new ActionObservabilityService(Optional.empty(), Optional.empty(), java.time.Clock.systemUTC())
        );
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
            // 事务存在时一定要等提交成功后再真正投递 MQ。
            // 否则会出现“消息已经发出，但主事务回滚”的经典脏消息问题。
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
                // 这里的 retry 只覆盖“事务已提交后的 MQ 投递失败”窗口，用于缩短消息滞留在 outbox 的时间。
                lastFailure = ex;
                int nextAttemptCount = attempt;
                ActionOutboxStatus nextStatus = attempt >= publishRetryMaxAttempts ? ActionOutboxStatus.DEAD : ActionOutboxStatus.NEW;
                currentOutbox = markOutbox(currentOutbox, nextStatus, nextAttemptCount);
                if (nextStatus == ActionOutboxStatus.DEAD) {
                    actionObservabilityService.outboxPublishFailed(currentOutbox, nextAttemptCount, ex.getMessage());
                }
            }
        }
        throw Objects.requireNonNull(lastFailure, "lastFailure must not be null");
    }

    private ActionOutbox markOutbox(ActionOutbox outbox, ActionOutboxStatus status, int attemptCount) {
        // 发布后只更新 outbox，自身不再改 action / step，保持“执行状态”和“投递状态”两个维度解耦。
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
