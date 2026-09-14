package io.github.actionguard.core.model;

import java.time.Instant;

/**
 * 不可变告警的可靠投递记录。
 *
 * <p>一条记录对应一个稳定 {@code eventId}。该记录不会随着 Action 步骤推进而复用，
 * 与执行消息 Outbox 的状态和计数语义完全独立。</p>
 */
public record ActionAlertOutbox(
        String id,
        String eventId,
        String dedupeKey,
        String type,
        String level,
        String title,
        String message,
        String actionName,
        String actionInstanceId,
        String stepName,
        String stepType,
        Instant occurredAt,
        String detailsJson,
        ActionAlertOutboxStatus status,
        Instant availableAt,
        int deliveryAttemptCount,
        String lastErrorMessage,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
}
