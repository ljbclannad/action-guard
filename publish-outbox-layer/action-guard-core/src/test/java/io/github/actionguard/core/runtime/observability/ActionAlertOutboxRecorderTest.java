package io.github.actionguard.core.runtime.observability;

import io.github.actionguard.api.runtime.ActionAlertEvent;
import io.github.actionguard.api.runtime.ActionAlertLevel;
import io.github.actionguard.api.runtime.ActionAlertType;
import io.github.actionguard.core.model.ActionAlertOutbox;
import io.github.actionguard.core.repository.InMemoryActionAlertOutboxRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionAlertOutboxRecorderTest {

    @Test
    void shouldTreatRepeatedBusinessEventAsIdempotentAndUseCompactDedupeKey() {
        InMemoryActionAlertOutboxRepository repository = new InMemoryActionAlertOutboxRepository();
        ActionAlertOutboxRecorder recorder = new ActionAlertOutboxRecorder(
                repository, Clock.fixed(Instant.parse("2026-09-12T08:00:00Z"), ZoneOffset.UTC));

        ActionAlertOutbox first = recorder.record(compensationFailed("first failure message"));
        ActionAlertOutbox repeated = recorder.record(compensationFailed("a different failure message"));

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(first.dedupeKey()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(first.detailsJson()).contains("first failure message");
    }

    private ActionAlertEvent compensationFailed(String message) {
        return new ActionAlertEvent(
                ActionAlertType.COMPENSATION_FAILED,
                ActionAlertLevel.HIGH,
                "action compensation failed",
                message,
                "order-flow",
                "action-1",
                "refund",
                "HTTP",
                Instant.parse("2026-09-12T08:00:00Z"),
                Map.of("stepIndex", "2", "resultMessage", message)
        );
    }
}
