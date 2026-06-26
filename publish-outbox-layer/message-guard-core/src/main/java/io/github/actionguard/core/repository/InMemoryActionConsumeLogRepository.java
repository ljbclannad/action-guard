package io.github.actionguard.core.repository;

import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.core.model.ActionConsumeLog;
import io.github.actionguard.core.model.ActionConsumeStatus;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryActionConsumeLogRepository implements ActionConsumeLogRepository {

    private final Map<String, ActionConsumeLog> storage = new ConcurrentHashMap<>();

    @Override
    public boolean tryStartConsumption(ActionExecutionMessage message, String consumerGroup, Instant now) {
        ActionConsumeLog log = new ActionConsumeLog(
                UUID.randomUUID().toString(),
                message.messageId(),
                message.actionInstanceId(),
                consumerGroup,
                ActionConsumeStatus.EXECUTING,
                message.messageKey(),
                1,
                null,
                0,
                now,
                now,
                now
        );
        return storage.putIfAbsent(message.messageId(), log) == null;
    }

    @Override
    public void markAcked(String messageId, String consumerGroup, Instant now) {
        storage.computeIfPresent(messageId, (key, existing) -> new ActionConsumeLog(
                existing.id(),
                existing.messageId(),
                existing.actionInstanceId(),
                consumerGroup,
                ActionConsumeStatus.ACKED,
                existing.dedupeKey(),
                existing.attemptCount(),
                existing.lastErrorMessage(),
                existing.version() + 1,
                existing.firstReceivedAt(),
                now,
                now
        ));
    }

    @Override
    public void markDuplicateSkipped(String messageId, String consumerGroup, Instant now) {
        storage.computeIfPresent(messageId, (key, existing) -> new ActionConsumeLog(
                existing.id(),
                existing.messageId(),
                existing.actionInstanceId(),
                consumerGroup,
                ActionConsumeStatus.DUPLICATE_SKIPPED,
                existing.dedupeKey(),
                existing.attemptCount() + 1,
                "duplicate delivery skipped",
                existing.version() + 1,
                existing.firstReceivedAt(),
                now,
                now
        ));
    }

    @Override
    public void markFailed(String messageId, String consumerGroup, Instant now, String errorMessage) {
        storage.computeIfPresent(messageId, (key, existing) -> new ActionConsumeLog(
                existing.id(),
                existing.messageId(),
                existing.actionInstanceId(),
                consumerGroup,
                ActionConsumeStatus.FAILED,
                existing.dedupeKey(),
                existing.attemptCount(),
                errorMessage,
                existing.version() + 1,
                existing.firstReceivedAt(),
                now,
                now
        ));
    }

    @Override
    public void markDeadLettered(String messageId, String consumerGroup, Instant now, String errorMessage) {
        storage.computeIfPresent(messageId, (key, existing) -> new ActionConsumeLog(
                existing.id(),
                existing.messageId(),
                existing.actionInstanceId(),
                consumerGroup,
                ActionConsumeStatus.DEAD_LETTERED,
                existing.dedupeKey(),
                existing.attemptCount(),
                errorMessage,
                existing.version() + 1,
                existing.firstReceivedAt(),
                now,
                now
        ));
    }

    @Override
    public Optional<ActionConsumeLog> findByMessageId(String messageId) {
        return Optional.ofNullable(storage.get(messageId));
    }
}
