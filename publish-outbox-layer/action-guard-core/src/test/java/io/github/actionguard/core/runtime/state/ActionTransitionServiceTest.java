package io.github.actionguard.core.runtime.state;

import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.repository.InMemoryActionInstanceRepository;
import io.github.actionguard.core.repository.InMemoryActionTransitionLogRepository;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ActionTransitionServiceTest {

    @Test
    void shouldApplyTransitionAndPersistActionAndTimelineLog() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionTransitionLogRepository transitionLogRepository = new InMemoryActionTransitionLogRepository();
        ActionTransitionService service = new ActionTransitionService(
                actionInstanceRepository,
                transitionLogRepository,
                new ActionObservabilityService(Optional.empty(), Optional.empty(), Clock.fixed(Instant.parse("2026-06-26T12:00:00Z"), ZoneOffset.UTC))
        );
        ActionInstance actionInstance = actionInstanceRepository.save(new ActionInstance(
                "act-1",
                "order-cancel-flow",
                "order:1",
                ActionStatus.DISPATCHING,
                0,
                2,
                Map.of(),
                null,
                null,
                0,
                Instant.parse("2026-06-26T12:00:00Z"),
                Instant.parse("2026-06-26T12:00:00Z")
        ));

        ActionTransitionExecution execution = service.transition(
                actionInstance,
                ActionTransitionEvent.MANUAL_SKIP_REQUESTED,
                ActionTransitionContext.atNextStep(1, Instant.parse("2026-06-26T12:01:00Z")),
                ActionTransitionMetadata.of(0, "send-user-sms", "SMS", "anonymous", null, null)
        );

        assertThat(execution.transitionResult().toStatus()).isEqualTo(ActionStatus.DISPATCHING);
        assertThat(execution.transitionResult().actionInstance().currentStepIndex()).isEqualTo(1);
        assertThat(actionInstanceRepository.findById("act-1").orElseThrow().currentStepIndex()).isEqualTo(1);
        assertThat(transitionLogRepository.findByActionInstanceId("act-1")).hasSize(1);
        assertThat(transitionLogRepository.findByActionInstanceId("act-1").get(0).operator()).isEqualTo("anonymous");
    }
}
