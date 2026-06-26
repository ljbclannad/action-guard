package io.github.actionguard.core.repository;

import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.core.model.ActionConsumeLog;

import java.time.Instant;
import java.util.Optional;

public interface ActionConsumeLogRepository {

    boolean tryStartConsumption(ActionExecutionMessage message, String consumerGroup, Instant now);

    void markAcked(String messageId, String consumerGroup, Instant now);

    void markDuplicateSkipped(String messageId, String consumerGroup, Instant now);

    void markFailed(String messageId, String consumerGroup, Instant now, String errorMessage);

    void markDeadLettered(String messageId, String consumerGroup, Instant now, String errorMessage);

    Optional<ActionConsumeLog> findByMessageId(String messageId);
}
