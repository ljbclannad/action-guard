package io.github.actionguard.ops.api.model;

import io.github.actionguard.core.model.ActionOutboxStatus;

import java.time.Instant;

/**
 * Action 关联 Outbox 的当前投递诊断快照。
 *
 * <p>{@code DONE} 只表示消息已成功发送并完成 Outbox 状态落库，不表示消息已经消费或 Action 已成功。
 * {@code attemptCount} 是累计调度计数；{@code deliveryAttemptCount} 才是消息发送失败次数。</p>
 */
public record ActionOutboxView(
        String id,
        String actionInstanceId,
        String topic,
        String dispatchId,
        ActionOutboxStatus status,
        Instant availableAt,
        int attemptCount,
        int deliveryAttemptCount,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
}
