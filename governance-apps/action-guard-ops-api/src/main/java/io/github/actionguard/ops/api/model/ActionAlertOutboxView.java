package io.github.actionguard.ops.api.model;

import io.github.actionguard.core.model.ActionAlertOutboxStatus;

import java.time.Instant;

/**
 * Action 关联告警 Outbox 的只读投递诊断快照。
 */
public record ActionAlertOutboxView(
        String id,
        String eventId,
        String type,
        String level,
        String actionName,
        String actionInstanceId,
        String stepName,
        String stepType,
        ActionAlertOutboxStatus status,
        Instant availableAt,
        int deliveryAttemptCount,
        String lastErrorMessage,
        Instant occurredAt,
        Instant createdAt,
        Instant updatedAt,
        int version
) {
}
