package io.github.actionguard.core.runtime.observability;

import io.github.actionguard.api.spi.ActionAlertSender;
import io.github.actionguard.core.model.ActionAlertOutbox;
import io.github.actionguard.core.repository.ActionAlertOutboxRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 扫描到期 NEW 和超时 CLAIMED 的告警 Outbox，恢复跨进程中断的外发。
 */
public class ActionAlertOutboxRecoveryService {

    private final ActionAlertOutboxRepository repository;
    private final Optional<ActionAlertSender> sender;
    private final ActionAlertOutboxDispatcher dispatcher;
    private final Clock clock;

    public ActionAlertOutboxRecoveryService(
            ActionAlertOutboxRepository repository,
            Optional<ActionAlertSender> sender,
            Clock clock,
            int maxDeliveryAttempts,
            Duration retryBackoff
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.sender = Objects.requireNonNull(sender, "sender must not be null");
        this.dispatcher = new ActionAlertOutboxDispatcher(repository, sender, clock, maxDeliveryAttempts, retryBackoff);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public int recoverDueAlerts(int batchSize, Duration claimTimeout) {
        if (batchSize <= 0 || sender.isEmpty()) {
            return 0;
        }
        Instant now = clock.instant();
        List<ActionAlertOutbox> candidates = repository.findRecoverable(now, now.minus(claimTimeout), batchSize);
        int deliveredCount = 0;
        for (ActionAlertOutbox candidate : candidates) {
            if (dispatcher.dispatch(candidate)) {
                deliveredCount++;
            }
        }
        return deliveredCount;
    }
}
