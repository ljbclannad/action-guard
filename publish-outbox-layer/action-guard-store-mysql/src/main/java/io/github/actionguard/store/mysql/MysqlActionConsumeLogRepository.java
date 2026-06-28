package io.github.actionguard.store.mysql;

import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.core.model.ActionConsumeLog;
import io.github.actionguard.core.model.ActionConsumeStatus;
import io.github.actionguard.core.repository.ActionConsumeLogRepository;
import io.github.actionguard.store.mysql.mapper.ActionConsumeLogMapper;
import io.github.actionguard.store.mysql.mapper.ActionConsumeLogRow;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class MysqlActionConsumeLogRepository implements ActionConsumeLogRepository {

    private final ActionConsumeLogMapper mapper;

    public MysqlActionConsumeLogRepository(ActionConsumeLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean tryStartConsumption(ActionExecutionMessage message, String consumerGroup, Instant now) {
        try {
            ActionConsumeLogRow row = new ActionConsumeLogRow();
            row.setId(UUID.randomUUID().toString());
            row.setMessageId(message.messageId());
            row.setActionInstanceId(message.actionInstanceId());
            row.setConsumerGroup(consumerGroup);
            row.setConsumeStatus(ActionConsumeStatus.EXECUTING.name());
            row.setDedupeKey(message.messageKey());
            row.setAttemptCount(1);
            row.setLastErrorMessage(null);
            row.setVersion(0);
            row.setFirstReceivedAt(Timestamp.from(now));
            row.setLastReceivedAt(Timestamp.from(now));
            row.setUpdatedAt(Timestamp.from(now));
            mapper.insert(row);
            return true;
        } catch (DuplicateKeyException ex) {
            ActionConsumeLogRow existing = mapper.selectByMessageId(message.messageId());
            if (existing == null || !ActionConsumeStatus.FAILED.name().equals(existing.getConsumeStatus())) {
                return false;
            }
            existing.setConsumerGroup(consumerGroup);
            existing.setConsumeStatus(ActionConsumeStatus.EXECUTING.name());
            existing.setAttemptCount(existing.getAttemptCount() + 1);
            existing.setLastErrorMessage(null);
            existing.setLastReceivedAt(Timestamp.from(now));
            existing.setUpdatedAt(Timestamp.from(now));
            return mapper.updateOptimistically(existing) == 1;
        }
    }

    @Override
    public void markAcked(String messageId, String consumerGroup, Instant now) {
        updateStatus(messageId, consumerGroup, now, ActionConsumeStatus.ACKED, null, false);
    }

    @Override
    public void markDuplicateSkipped(String messageId, String consumerGroup, Instant now) {
        updateStatus(messageId, consumerGroup, now, ActionConsumeStatus.DUPLICATE_SKIPPED, "duplicate delivery skipped", true);
    }

    @Override
    public void markFailed(String messageId, String consumerGroup, Instant now, String errorMessage) {
        updateStatus(messageId, consumerGroup, now, ActionConsumeStatus.FAILED, errorMessage, false);
    }

    @Override
    public void markDeadLettered(String messageId, String consumerGroup, Instant now, String errorMessage) {
        updateStatus(messageId, consumerGroup, now, ActionConsumeStatus.DEAD_LETTERED, errorMessage, false);
    }

    @Override
    public Optional<ActionConsumeLog> findByMessageId(String messageId) {
        return Optional.ofNullable(mapper.selectByMessageId(messageId)).map(this::toModel);
    }

    private void updateStatus(
            String messageId,
            String consumerGroup,
            Instant now,
            ActionConsumeStatus status,
            String errorMessage,
            boolean incrementAttemptCount
    ) {
        ActionConsumeLogRow existing = mapper.selectByMessageId(messageId);
        if (existing == null) {
            throw new IllegalStateException("ActionConsumeLog not found: " + messageId);
        }
        existing.setConsumerGroup(consumerGroup);
        existing.setConsumeStatus(status.name());
        existing.setLastErrorMessage(errorMessage);
        if (incrementAttemptCount) {
            existing.setAttemptCount(existing.getAttemptCount() + 1);
        }
        existing.setLastReceivedAt(Timestamp.from(now));
        existing.setUpdatedAt(Timestamp.from(now));
        if (mapper.updateOptimistically(existing) != 1) {
            throw new OptimisticLockingFailureException("ActionConsumeLog version conflict: " + messageId);
        }
    }

    private ActionConsumeLog toModel(ActionConsumeLogRow row) {
        return new ActionConsumeLog(
                row.getId(),
                row.getMessageId(),
                row.getActionInstanceId(),
                row.getConsumerGroup(),
                ActionConsumeStatus.valueOf(row.getConsumeStatus()),
                row.getDedupeKey(),
                row.getAttemptCount(),
                row.getLastErrorMessage(),
                row.getVersion(),
                row.getFirstReceivedAt().toInstant(),
                row.getLastReceivedAt().toInstant(),
                row.getUpdatedAt().toInstant()
        );
    }
}
