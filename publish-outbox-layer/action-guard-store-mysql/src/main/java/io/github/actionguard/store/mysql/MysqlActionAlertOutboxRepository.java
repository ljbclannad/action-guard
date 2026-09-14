package io.github.actionguard.store.mysql;

import io.github.actionguard.core.model.ActionAlertOutbox;
import io.github.actionguard.core.model.ActionAlertOutboxStatus;
import io.github.actionguard.core.repository.ActionAlertOutboxRepository;
import io.github.actionguard.store.mysql.mapper.ActionAlertOutboxMapper;
import io.github.actionguard.store.mysql.mapper.ActionAlertOutboxRow;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class MysqlActionAlertOutboxRepository implements ActionAlertOutboxRepository {

    private final ActionAlertOutboxMapper mapper;

    public MysqlActionAlertOutboxRepository(ActionAlertOutboxMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ActionAlertOutbox save(ActionAlertOutbox outbox) {
        ActionAlertOutboxRow existing = mapper.selectById(outbox.id());
        if (existing == null) {
            try {
                mapper.insert(toRow(outbox));
            } catch (org.springframework.dao.DataIntegrityViolationException exception) {
                throw new DuplicateKeyException("ActionAlertOutbox unique key conflict: " + outbox.dedupeKey(), exception);
            }
            return outbox;
        }
        if (mapper.updateOptimistically(toRow(outbox)) != 1) {
            throw new OptimisticLockingFailureException("ActionAlertOutbox version conflict: " + outbox.id());
        }
        return withNextVersion(outbox);
    }

    @Override
    public Optional<ActionAlertOutbox> findByDedupeKey(String dedupeKey) {
        return Optional.ofNullable(mapper.selectByDedupeKey(dedupeKey)).map(this::toModel);
    }

    @Override
    public Optional<ActionAlertOutbox> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toModel);
    }

    @Override
    public List<ActionAlertOutbox> findRecoverable(Instant availableBeforeOrAt, Instant claimedBeforeOrAt, int limit) {
        return mapper.selectRecoverable(Timestamp.from(availableBeforeOrAt), Timestamp.from(claimedBeforeOrAt), limit)
                .stream().map(this::toModel).toList();
    }

    private ActionAlertOutbox withNextVersion(ActionAlertOutbox outbox) {
        return new ActionAlertOutbox(
                outbox.id(), outbox.eventId(), outbox.dedupeKey(), outbox.type(), outbox.level(), outbox.title(),
                outbox.message(), outbox.actionName(), outbox.actionInstanceId(), outbox.stepName(), outbox.stepType(),
                outbox.occurredAt(), outbox.detailsJson(), outbox.status(), outbox.availableAt(),
                outbox.deliveryAttemptCount(), outbox.lastErrorMessage(), outbox.version() + 1, outbox.createdAt(), outbox.updatedAt()
        );
    }

    private ActionAlertOutboxRow toRow(ActionAlertOutbox outbox) {
        ActionAlertOutboxRow row = new ActionAlertOutboxRow();
        row.setId(outbox.id());
        row.setEventId(outbox.eventId());
        row.setDedupeKey(outbox.dedupeKey());
        row.setType(outbox.type());
        row.setLevel(outbox.level());
        row.setTitle(outbox.title());
        row.setMessage(outbox.message());
        row.setActionName(outbox.actionName());
        row.setActionInstanceId(outbox.actionInstanceId());
        row.setStepName(outbox.stepName());
        row.setStepType(outbox.stepType());
        row.setOccurredAt(Timestamp.from(outbox.occurredAt()));
        row.setDetailsJson(outbox.detailsJson());
        row.setStatus(outbox.status().name());
        row.setAvailableAt(Timestamp.from(outbox.availableAt()));
        row.setDeliveryAttemptCount(outbox.deliveryAttemptCount());
        row.setLastErrorMessage(outbox.lastErrorMessage());
        row.setVersion(outbox.version());
        row.setCreatedAt(Timestamp.from(outbox.createdAt()));
        row.setUpdatedAt(Timestamp.from(outbox.updatedAt()));
        return row;
    }

    private ActionAlertOutbox toModel(ActionAlertOutboxRow row) {
        return new ActionAlertOutbox(
                row.getId(), row.getEventId(), row.getDedupeKey(), row.getType(), row.getLevel(), row.getTitle(),
                row.getMessage(), row.getActionName(), row.getActionInstanceId(), row.getStepName(), row.getStepType(),
                row.getOccurredAt().toInstant(), row.getDetailsJson(), ActionAlertOutboxStatus.valueOf(row.getStatus()),
                row.getAvailableAt().toInstant(), row.getDeliveryAttemptCount(), row.getLastErrorMessage(), row.getVersion(),
                row.getCreatedAt().toInstant(), row.getUpdatedAt().toInstant()
        );
    }
}
