package io.github.actionguard.core.runtime.observability;

import io.github.actionguard.api.runtime.ActionAlertEvent;
import io.github.actionguard.api.runtime.ActionAlertLevel;
import io.github.actionguard.api.runtime.ActionAlertType;
import io.github.actionguard.api.spi.ActionAlertSender;
import io.github.actionguard.core.model.ActionAlertOutbox;
import io.github.actionguard.core.model.ActionAlertOutboxStatus;
import io.github.actionguard.core.repository.InMemoryActionAlertOutboxRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ActionAlertOutboxRecoveryServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-12T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldKeepNewAlertWhenSenderIsMissing() {
        InMemoryActionAlertOutboxRepository repository = new InMemoryActionAlertOutboxRepository();
        ActionAlertOutbox recorded = new ActionAlertOutboxRecorder(repository, clock).record(event());
        ActionAlertOutboxRecoveryService recoveryService = new ActionAlertOutboxRecoveryService(
                repository, Optional.empty(), clock, 3, Duration.ofSeconds(5));

        assertThat(recoveryService.recoverDueAlerts(10, Duration.ofSeconds(30))).isZero();
        assertThat(repository.findById(recorded.id())).get().satisfies(outbox -> {
            assertThat(outbox.status()).isEqualTo(ActionAlertOutboxStatus.NEW);
            assertThat(outbox.deliveryAttemptCount()).isZero();
        });
    }

    @Test
    void shouldRecoverExpiredClaimAndDeliverIt() {
        InMemoryActionAlertOutboxRepository repository = new InMemoryActionAlertOutboxRepository();
        Instant createdAt = clock.instant().minusSeconds(120);
        ActionAlertOutbox claimed = new ActionAlertOutbox(
                "alert-1", "event-1", "dedupe-1", "RETRIES_EXHAUSTED", "HIGH", "retries exhausted", "failed",
                "order-flow", "action-1", "notify", "HTTP", createdAt, "{}", ActionAlertOutboxStatus.CLAIMED,
                createdAt, 0, null, 0, createdAt, createdAt
        );
        repository.save(claimed);
        CapturingSender sender = new CapturingSender();
        ActionAlertOutboxRecoveryService recoveryService = new ActionAlertOutboxRecoveryService(
                repository, Optional.of(sender), clock, 3, Duration.ofSeconds(5));

        assertThat(recoveryService.recoverDueAlerts(10, Duration.ofSeconds(30))).isEqualTo(1);
        assertThat(sender.eventIds).containsExactly("event-1");
        assertThat(repository.findById("alert-1")).get().extracting(ActionAlertOutbox::status)
                .isEqualTo(ActionAlertOutboxStatus.DONE);
    }

    private ActionAlertEvent event() {
        return new ActionAlertEvent(
                ActionAlertType.RETRIES_EXHAUSTED,
                ActionAlertLevel.HIGH,
                "action retries exhausted",
                "send failed",
                "order-flow",
                "action-1",
                "send-notification",
                "HTTP",
                clock.instant(),
                Map.of("stepIndex", "0", "attemptCount", "3")
        );
    }

    private static final class CapturingSender implements ActionAlertSender {
        private final List<String> eventIds = new java.util.ArrayList<>();

        @Override
        public void send(ActionAlertEvent event) {
            eventIds.add(event.eventId());
        }
    }
}
