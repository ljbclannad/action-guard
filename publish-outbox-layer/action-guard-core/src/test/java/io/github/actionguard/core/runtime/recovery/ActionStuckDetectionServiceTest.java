package io.github.actionguard.core.runtime.recovery;

import io.github.actionguard.core.runtime.observability.ActionObservabilityService;

import io.github.actionguard.api.runtime.ActionAlertEvent;
import io.github.actionguard.api.runtime.ActionAlertType;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.repository.InMemoryActionInstanceRepository;
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

class ActionStuckDetectionServiceTest {

    @Test
    void shouldAlertStuckActionsOnlyOnceForSameFingerprint() {
        InMemoryActionInstanceRepository repository = new InMemoryActionInstanceRepository();
        Instant createdAt = Instant.parse("2026-06-26T12:00:00Z");
        Instant updatedAt = Instant.parse("2026-06-26T12:01:00Z");
        repository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.DISPATCHING, 0, 2, Map.of(),
                null, null, 0, createdAt, updatedAt
        ));
        CapturingAlertPublisher publisher = new CapturingAlertPublisher();
        ActionObservabilityService observabilityService = new ActionObservabilityService(
                Optional.of(publisher),
                Optional.empty(),
                Clock.fixed(Instant.parse("2026-06-26T12:10:00Z"), ZoneOffset.UTC)
        );
        ActionStuckDetectionService service = new ActionStuckDetectionService(
                repository,
                observabilityService,
                Clock.fixed(Instant.parse("2026-06-26T12:10:00Z"), ZoneOffset.UTC)
        );

        int firstDetected = service.detectStuckActions(10, Duration.ofMinutes(5));
        int secondDetected = service.detectStuckActions(10, Duration.ofMinutes(5));

        assertThat(firstDetected).isEqualTo(1);
        assertThat(secondDetected).isZero();
        assertThat(publisher.events).hasSize(1);
        assertThat(publisher.events.get(0).type()).isEqualTo(ActionAlertType.ACTION_STUCK);
    }

    private static final class CapturingAlertPublisher implements io.github.actionguard.api.spi.ActionAlertPublisher {
        private final List<ActionAlertEvent> events = new ArrayList<>();

        @Override
        public void publish(ActionAlertEvent event) {
            events.add(event);
        }
    }
}
