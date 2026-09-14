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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ActionAlertOutboxDispatcherTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-12T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldReplayPersistedDetailsAndKeepEventIdStable() {
        InMemoryActionAlertOutboxRepository repository = new InMemoryActionAlertOutboxRepository();
        ActionAlertOutbox recorded = new ActionAlertOutboxRecorder(repository, clock).record(event());
        CapturingSender sender = new CapturingSender();
        ActionAlertOutboxDispatcher dispatcher = new ActionAlertOutboxDispatcher(
                repository, Optional.of(sender), clock, 3, Duration.ofSeconds(5));

        assertThat(dispatcher.dispatch(recorded)).isTrue();

        assertThat(sender.events).singleElement().satisfies(sent -> {
            assertThat(sent.eventId()).isEqualTo(recorded.eventId());
            assertThat(sent.details()).containsExactlyEntriesOf(event().details());
        });
        assertThat(repository.findById(recorded.id())).get().extracting(ActionAlertOutbox::status)
                .isEqualTo(ActionAlertOutboxStatus.DONE);
    }

    @Test
    void shouldBackOffAfterSenderFailureWithoutChangingEventId() {
        InMemoryActionAlertOutboxRepository repository = new InMemoryActionAlertOutboxRepository();
        ActionAlertOutbox recorded = new ActionAlertOutboxRecorder(repository, clock).record(event());
        ActionAlertOutboxDispatcher dispatcher = new ActionAlertOutboxDispatcher(
                repository, Optional.of(event -> {
            throw new IllegalStateException("webhook https://example.test/hook token=secret");
        }), clock, 3, Duration.ofSeconds(5));

        assertThat(dispatcher.dispatch(recorded)).isFalse();

        assertThat(repository.findById(recorded.id())).get().satisfies(outbox -> {
            assertThat(outbox.status()).isEqualTo(ActionAlertOutboxStatus.NEW);
            assertThat(outbox.deliveryAttemptCount()).isEqualTo(1);
            assertThat(outbox.availableAt()).isEqualTo(clock.instant().plusSeconds(5));
            assertThat(outbox.eventId()).isEqualTo(recorded.eventId());
            assertThat(outbox.lastErrorMessage()).contains("[REDACTED_URL]").contains("token=[REDACTED]")
                    .doesNotContain("example.test").doesNotContain("secret");
        });
    }

    @Test
    void shouldMarkDeadAfterMaximumSenderFailures() {
        InMemoryActionAlertOutboxRepository repository = new InMemoryActionAlertOutboxRepository();
        ActionAlertOutbox recorded = new ActionAlertOutboxRecorder(repository, clock).record(event());
        ActionAlertOutboxDispatcher dispatcher = new ActionAlertOutboxDispatcher(
                repository, Optional.of(event -> {
            throw new IllegalStateException("channel failed");
        }), clock, 1, Duration.ofSeconds(5));

        assertThat(dispatcher.dispatch(recorded)).isFalse();

        assertThat(repository.findById(recorded.id())).get().satisfies(outbox -> {
            assertThat(outbox.status()).isEqualTo(ActionAlertOutboxStatus.DEAD);
            assertThat(outbox.deliveryAttemptCount()).isEqualTo(1);
        });
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
                Map.of("stepIndex", "0", "attemptCount", "3", "errorCode", "HTTP_500")
        );
    }

    private static final class CapturingSender implements ActionAlertSender {
        private final List<ActionAlertEvent> events = new ArrayList<>();

        @Override
        public void send(ActionAlertEvent event) {
            events.add(event);
        }
    }
}
