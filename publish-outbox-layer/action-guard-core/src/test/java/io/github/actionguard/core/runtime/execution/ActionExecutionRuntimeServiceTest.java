package io.github.actionguard.core.runtime.execution;

import io.github.actionguard.api.runtime.StepExecutionResult;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.model.ActionStepStatus;
import io.github.actionguard.core.repository.InMemoryActionInstanceRepository;
import io.github.actionguard.core.repository.InMemoryActionStepInstanceRepository;
import io.github.actionguard.core.repository.InMemoryActionTransitionLogRepository;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import io.github.actionguard.core.runtime.state.ActionTransitionEvent;
import io.github.actionguard.core.runtime.state.ActionTransitionService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ActionExecutionRuntimeServiceTest {

    @Test
    void shouldPersistSuccessfulStepAndAdvanceAction() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionStepInstanceRepository stepRepository = new InMemoryActionStepInstanceRepository();
        InMemoryActionTransitionLogRepository transitionLogRepository = new InMemoryActionTransitionLogRepository();
        ActionExecutionRuntimeService service = new ActionExecutionRuntimeService(
                stepRepository,
                new ActionTransitionService(
                        actionInstanceRepository,
                        transitionLogRepository,
                        new ActionObservabilityService(Optional.empty(), Optional.empty(), Clock.fixed(Instant.parse("2026-06-26T12:00:00Z"), ZoneOffset.UTC))
                )
        );
        ActionInstance actionInstance = actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.DISPATCHING, 0, 2, Map.of(),
                null, null, 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));
        ActionStepInstance currentStep = stepRepository.save(new ActionStepInstance(
                "step-1", "act-1", 0, "send-user-sms", "SMS", "notify.user", ActionStepStatus.PENDING, 0, Map.of(),
                null, null, 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));

        ActionExecutionProgress progress = service.completeStepSuccess(
                actionInstance,
                currentStep,
                1,
                Instant.parse("2026-06-26T12:01:00Z")
        );

        assertThat(progress.stepInstance().status()).isEqualTo(ActionStepStatus.SUCCESS);
        assertThat(progress.transitionExecution().transitionResult().actionInstance().currentStepIndex()).isEqualTo(1);
        assertThat(actionInstanceRepository.findById("act-1").orElseThrow().currentStepIndex()).isEqualTo(1);
    }

    @Test
    void shouldPersistFailedStepBeforeTransitioningAction() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionStepInstanceRepository stepRepository = new InMemoryActionStepInstanceRepository();
        InMemoryActionTransitionLogRepository transitionLogRepository = new InMemoryActionTransitionLogRepository();
        ActionExecutionRuntimeService service = new ActionExecutionRuntimeService(
                stepRepository,
                new ActionTransitionService(
                        actionInstanceRepository,
                        transitionLogRepository,
                        new ActionObservabilityService(Optional.empty(), Optional.empty(), Clock.fixed(Instant.parse("2026-06-26T12:00:00Z"), ZoneOffset.UTC))
                )
        );
        ActionInstance actionInstance = actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.DISPATCHING, 0, 1, Map.of(),
                null, null, 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));
        ActionStepInstance currentStep = stepRepository.save(new ActionStepInstance(
                "step-1", "act-1", 0, "send-user-sms", "SMS", "notify.user", ActionStepStatus.PENDING, 0, Map.of(),
                null, null, 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));
        StepExecutionResult result = StepExecutionResult.failed("DOWNSTREAM_ERROR", "sms provider failed");

        ActionStepInstance failedStep = service.persistFailedStep(
                currentStep,
                result,
                "sms provider failed",
                Instant.parse("2026-06-26T12:01:00Z")
        );
        assertThat(failedStep.status()).isEqualTo(ActionStepStatus.FAILED);
        assertThat(actionInstanceRepository.findById("act-1").orElseThrow().status()).isEqualTo(ActionStatus.DISPATCHING);

        var transition = service.transitionFailure(
                actionInstance,
                failedStep,
                result,
                "sms provider failed",
                ActionTransitionEvent.STEP_FAILED_TERMINAL,
                Instant.parse("2026-06-26T12:01:00Z")
        );

        assertThat(transition.transitionResult().toStatus()).isEqualTo(ActionStatus.FAILED);
        assertThat(actionInstanceRepository.findById("act-1").orElseThrow().status()).isEqualTo(ActionStatus.FAILED);
    }
}
