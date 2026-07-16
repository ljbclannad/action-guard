package io.github.actionguard.store.mysql;

import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.store.mysql.mapper.ActionOutboxMapper;
import io.github.actionguard.store.mysql.mapper.ActionOutboxRow;
import org.springframework.dao.OptimisticLockingFailureException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class MysqlActionOutboxRepository implements ActionOutboxRepository {

    private final ActionOutboxMapper mapper;

    public MysqlActionOutboxRepository(ActionOutboxMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ActionOutbox save(ActionOutbox outbox) {
        ActionOutboxRow row = toRow(outbox);
        ActionOutboxRow existing = mapper.selectById(outbox.id());
        if (existing == null) {
            mapper.insert(row);
            return outbox;
        }
        if (mapper.updateOptimistically(row) != 1) {
            throw new OptimisticLockingFailureException("ActionOutbox version conflict: " + outbox.id());
        }
        return new ActionOutbox(
                outbox.id(),
                outbox.actionInstanceId(),
                outbox.topic(),
                outbox.dispatchId(),
                outbox.status(),
                outbox.availableAt(),
                outbox.attemptCount(),
                outbox.version() + 1,
                outbox.createdAt(),
                outbox.updatedAt()
        );
    }

    @Override
    public Optional<ActionOutbox> findByActionInstanceId(String actionInstanceId) {
        return Optional.ofNullable(mapper.selectByActionInstanceId(actionInstanceId)).map(this::toModel);
    }

    @Override
    public Optional<ActionOutbox> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toModel);
    }

    @Override
    public List<ActionOutbox> findRecoverable(Instant availableBeforeOrAt, Instant claimedBeforeOrAt, int limit) {
        return mapper.selectRecoverable(
                        Timestamp.from(availableBeforeOrAt),
                        Timestamp.from(claimedBeforeOrAt),
                        limit
                ).stream()
                .map(this::toModel)
                .toList();
    }

    private ActionOutboxRow toRow(ActionOutbox outbox) {
        ActionOutboxRow row = new ActionOutboxRow();
        row.setId(outbox.id());
        row.setActionInstanceId(outbox.actionInstanceId());
        row.setTopic(outbox.topic());
        row.setDispatchId(outbox.dispatchId());
        row.setStatus(outbox.status().name());
        row.setAvailableAt(Timestamp.from(outbox.availableAt()));
        row.setAttemptCount(outbox.attemptCount());
        row.setVersion(outbox.version());
        row.setCreatedAt(Timestamp.from(outbox.createdAt()));
        row.setUpdatedAt(Timestamp.from(outbox.updatedAt()));
        return row;
    }

    private ActionOutbox toModel(ActionOutboxRow row) {
        return new ActionOutbox(
                row.getId(),
                row.getActionInstanceId(),
                row.getTopic(),
                row.getDispatchId(),
                ActionOutboxStatus.valueOf(row.getStatus()),
                row.getAvailableAt().toInstant(),
                row.getAttemptCount(),
                row.getVersion(),
                row.getCreatedAt().toInstant(),
                row.getUpdatedAt().toInstant()
        );
    }
}
