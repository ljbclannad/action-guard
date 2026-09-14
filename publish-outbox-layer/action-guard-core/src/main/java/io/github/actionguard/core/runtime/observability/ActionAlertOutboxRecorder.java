package io.github.actionguard.core.runtime.observability;

import io.github.actionguard.api.runtime.ActionAlertEvent;
import io.github.actionguard.core.model.ActionAlertOutbox;
import io.github.actionguard.core.model.ActionAlertOutboxStatus;
import io.github.actionguard.core.repository.ActionAlertOutboxRepository;
import org.springframework.dao.DuplicateKeyException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 将业务告警原子记录为独立的可靠投递任务。
 */
public class ActionAlertOutboxRecorder {

    private final ActionAlertOutboxRepository repository;
    private final Clock clock;

    public ActionAlertOutboxRecorder(ActionAlertOutboxRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 相同语义事件的重复调用返回既有记录；其他持久化异常向上抛出，使外层业务事务回滚。
     */
    public ActionAlertOutbox record(ActionAlertEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        String dedupeKey = dedupeKey(event);
        return repository.findByDedupeKey(dedupeKey).orElseGet(() -> saveOrFindExisting(event, dedupeKey));
    }

    private ActionAlertOutbox saveOrFindExisting(ActionAlertEvent event, String dedupeKey) {
        Instant now = clock.instant();
        ActionAlertOutbox candidate = new ActionAlertOutbox(
                UUID.randomUUID().toString(),
                event.eventId() == null || event.eventId().isBlank() ? UUID.randomUUID().toString() : event.eventId(),
                dedupeKey,
                event.type().name(),
                event.level().name(),
                event.title(),
                event.message(),
                event.actionName(),
                event.actionInstanceId(),
                event.stepName(),
                event.stepType(),
                event.occurredAt() == null ? now : event.occurredAt(),
                ActionAlertOutboxPayloadCodec.serialize(event.details()),
                ActionAlertOutboxStatus.NEW,
                now,
                0,
                null,
                0,
                now,
                now
        );
        try {
            return repository.save(candidate);
        } catch (DuplicateKeyException exception) {
            return repository.findByDedupeKey(dedupeKey).orElseThrow(() -> exception);
        }
    }

    private String dedupeKey(ActionAlertEvent event) {
        StringBuilder source = new StringBuilder(event.type().name());
        append(source, event.actionInstanceId());
        append(source, event.stepName());
        append(source, event.stepType());
        Map<String, String> details = event.details() == null ? Map.of() : event.details();
        switch (event.type()) {
            case RETRIES_EXHAUSTED -> {
                append(source, details.get("stepIndex"));
                append(source, details.get("attemptCount"));
            }
            case COMPENSATION_FAILED -> append(source, details.get("stepIndex"));
            case CONSUME_FAILURE, DEAD_LETTER -> {
                append(source, details.get("consumerGroup"));
                append(source, details.get("messageId"));
            }
            case OUTBOX_PUBLISH_FAILED -> {
                append(source, details.get("outboxId"));
                append(source, details.get("attemptedCount"));
            }
            case OUTBOX_DEAD -> {
                append(source, details.get("outboxId"));
                append(source, details.get("dispatchId"));
            }
            case ACTION_STUCK -> append(source, details.get("updatedAt"));
            default -> {
                append(source, event.title());
                append(source, event.message());
            }
        }
        return sha256(source.toString());
    }

    private void append(StringBuilder builder, String value) {
        builder.append('|').append(value == null ? "" : value);
    }

    private String sha256(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
