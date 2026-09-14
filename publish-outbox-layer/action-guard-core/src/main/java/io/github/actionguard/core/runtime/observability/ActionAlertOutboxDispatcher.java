package io.github.actionguard.core.runtime.observability;

import io.github.actionguard.api.runtime.ActionAlertEvent;
import io.github.actionguard.api.runtime.ActionAlertLevel;
import io.github.actionguard.api.runtime.ActionAlertType;
import io.github.actionguard.api.spi.ActionAlertSender;
import io.github.actionguard.core.model.ActionAlertOutbox;
import io.github.actionguard.core.model.ActionAlertOutboxStatus;
import io.github.actionguard.core.repository.ActionAlertOutboxRepository;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 单条告警 Outbox 的抢占、一次外发、完成与失败回退状态机。
 */
public class ActionAlertOutboxDispatcher {

    private final ActionAlertOutboxRepository repository;
    private final ActionAlertSender sender;
    private final Clock clock;
    private final int maxDeliveryAttempts;
    private final Duration retryBackoff;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public ActionAlertOutboxDispatcher(
            ActionAlertOutboxRepository repository,
            Optional<ActionAlertSender> sender,
            Clock clock,
            int maxDeliveryAttempts,
            Duration retryBackoff
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.sender = Objects.requireNonNull(sender, "sender must not be null").orElse(null);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.maxDeliveryAttempts = Math.max(1, maxDeliveryAttempts);
        this.retryBackoff = Objects.requireNonNull(retryBackoff, "retryBackoff must not be null").isNegative()
                ? Duration.ZERO : retryBackoff;
    }

    public boolean dispatch(ActionAlertOutbox candidate) {
        if (sender == null || (candidate.status() != ActionAlertOutboxStatus.NEW
                && candidate.status() != ActionAlertOutboxStatus.CLAIMED)
                || candidate.availableAt().isAfter(clock.instant())) {
            return false;
        }
        final ActionAlertOutbox claimed;
        try {
            claimed = save(candidate, ActionAlertOutboxStatus.CLAIMED, candidate.availableAt(),
                    candidate.deliveryAttemptCount(), candidate.lastErrorMessage());
        } catch (OptimisticLockingFailureException exception) {
            return false;
        }
        try {
            sender.send(toEvent(claimed));
        } catch (RuntimeException exception) {
            int deliveryAttemptCount = claimed.deliveryAttemptCount() + 1;
            ActionAlertOutboxStatus status = deliveryAttemptCount >= maxDeliveryAttempts
                    ? ActionAlertOutboxStatus.DEAD : ActionAlertOutboxStatus.NEW;
            Instant availableAt = status == ActionAlertOutboxStatus.DEAD
                    ? claimed.availableAt() : clock.instant().plus(retryBackoff);
            try {
                save(claimed, status, availableAt, deliveryAttemptCount, safeErrorMessage(exception));
            } catch (OptimisticLockingFailureException ignored) {
                // 状态已被其他执行者推进时不使用旧快照覆盖。
            }
            return false;
        }
        try {
            save(claimed, ActionAlertOutboxStatus.DONE, claimed.availableAt(), claimed.deliveryAttemptCount(),
                    claimed.lastErrorMessage());
            return true;
        } catch (OptimisticLockingFailureException exception) {
            // sender 已成功、完成落库失败时允许后续恢复扫描重投，保持 at-least-once。
            return false;
        }
    }

    private ActionAlertOutbox save(
            ActionAlertOutbox outbox,
            ActionAlertOutboxStatus status,
            Instant availableAt,
            int deliveryAttemptCount,
            String lastErrorMessage
    ) {
        return repository.save(new ActionAlertOutbox(
                outbox.id(), outbox.eventId(), outbox.dedupeKey(), outbox.type(), outbox.level(), outbox.title(),
                outbox.message(), outbox.actionName(), outbox.actionInstanceId(), outbox.stepName(), outbox.stepType(),
                outbox.occurredAt(), outbox.detailsJson(), status, availableAt, deliveryAttemptCount, lastErrorMessage,
                outbox.version(), outbox.createdAt(), clock.instant()
        ));
    }

    private ActionAlertEvent toEvent(ActionAlertOutbox outbox) {
        return new ActionAlertEvent(
                outbox.eventId(),
                ActionAlertType.valueOf(outbox.type()),
                ActionAlertLevel.valueOf(outbox.level()),
                outbox.title(), outbox.message(), outbox.actionName(), outbox.actionInstanceId(), outbox.stepName(),
                outbox.stepType(), outbox.occurredAt(), ActionAlertOutboxPayloadCodec.deserialize(outbox.detailsJson())
        );
    }

    private String safeErrorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        String sanitized = message
                .replaceAll("(?i)(authorization|token|password|secret)=?[^\\s,;]+", "$1=[REDACTED]")
                .replaceAll("https?://[^\\s,;]+", "[REDACTED_URL]");
        return sanitized.length() > 512 ? sanitized.substring(0, 512) : sanitized;
    }
}
