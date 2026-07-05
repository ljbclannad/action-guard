package io.github.actionguard.core.runtime.state;

import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionStateMachineTest {

    @Test
    void shouldDefineFullActionLifecycleTransitions() {
        assertThat(ActionStateMachine.canTransition(ActionStatus.NEW, ActionStatus.DISPATCHING)).isTrue();
        assertThat(ActionStateMachine.canTransition(ActionStatus.NEW, ActionStatus.SUCCESS)).isTrue();
        assertThat(ActionStateMachine.canTransition(ActionStatus.NEW, ActionStatus.IGNORED)).isTrue();
        assertThat(ActionStateMachine.canTransition(ActionStatus.DISPATCHING, ActionStatus.SUCCESS)).isTrue();
        assertThat(ActionStateMachine.canTransition(ActionStatus.DISPATCHING, ActionStatus.IGNORED)).isTrue();
        assertThat(ActionStateMachine.canTransition(ActionStatus.RETRYING, ActionStatus.FAILED)).isTrue();
        assertThat(ActionStateMachine.canTransition(ActionStatus.FAILED, ActionStatus.COMPENSATING)).isTrue();
        assertThat(ActionStateMachine.canTransition(ActionStatus.DEAD, ActionStatus.COMPENSATING)).isTrue();
        assertThat(ActionStateMachine.canTransition(ActionStatus.COMPENSATING, ActionStatus.COMPENSATED)).isTrue();
        assertThat(ActionStateMachine.canTransition(ActionStatus.COMPENSATING, ActionStatus.DEAD)).isTrue();

        assertThat(ActionStateMachine.canTransition(ActionStatus.SUCCESS, ActionStatus.RETRYING)).isFalse();
        assertThat(ActionStateMachine.canTransition(ActionStatus.FAILED, ActionStatus.DISPATCHING)).isFalse();
        assertThat(ActionStateMachine.canTransition(ActionStatus.COMPENSATED, ActionStatus.COMPENSATING)).isFalse();
        assertThat(ActionStateMachine.canTransition(ActionStatus.IGNORED, ActionStatus.DISPATCHING)).isFalse();
    }

    @Test
    void shouldDefineCommandPermissionsFromSingleSourceOfTruth() {
        assertThat(ActionStateMachine.canExecute(ActionStatus.FAILED, ActionCommand.RETRY)).isTrue();
        assertThat(ActionStateMachine.canExecute(ActionStatus.RETRYING, ActionCommand.SKIP)).isTrue();
        assertThat(ActionStateMachine.canExecute(ActionStatus.NEW, ActionCommand.CANCEL)).isTrue();
        assertThat(ActionStateMachine.canExecute(ActionStatus.DEAD, ActionCommand.COMPENSATE)).isTrue();

        assertThat(ActionStateMachine.canExecute(ActionStatus.SUCCESS, ActionCommand.RETRY)).isFalse();
        assertThat(ActionStateMachine.canExecute(ActionStatus.FAILED, ActionCommand.CANCEL)).isFalse();
        assertThat(ActionStateMachine.canExecute(ActionStatus.COMPENSATED, ActionCommand.COMPENSATE)).isFalse();
    }

    @Test
    void shouldBuildTransitionResultFromFailureEvent() {
        ActionInstance current = new ActionInstance(
                "act-1",
                "order-cancel-flow",
                "order:1",
                ActionStatus.DISPATCHING,
                0,
                2,
                Map.of("operator", "demo"),
                null,
                null,
                3,
                Instant.parse("2026-06-26T09:00:00Z"),
                Instant.parse("2026-06-26T09:01:00Z")
        );

        ActionTransitionResult result = ActionStateMachine.apply(
                current,
                ActionTransitionEvent.STEP_FAILED_TERMINAL,
                ActionTransitionContext.failure(
                        0,
                        "DOWNSTREAM_ERROR",
                        "sms provider failed",
                        Instant.parse("2026-06-26T09:02:00Z")
                )
        );
        ActionInstance transitioned = result.actionInstance();

        assertThat(result.fromStatus()).isEqualTo(ActionStatus.DISPATCHING);
        assertThat(result.toStatus()).isEqualTo(ActionStatus.FAILED);
        assertThat(result.event()).isEqualTo(ActionTransitionEvent.STEP_FAILED_TERMINAL);
        assertThat(transitioned.status()).isEqualTo(ActionStatus.FAILED);
        assertThat(transitioned.currentStepIndex()).isEqualTo(0);
        assertThat(transitioned.lastErrorCode()).isEqualTo("DOWNSTREAM_ERROR");
        assertThat(transitioned.lastErrorMessage()).isEqualTo("sms provider failed");
        assertThat(transitioned.version()).isEqualTo(3);
    }

    @Test
    void shouldResolveStepSuccessEventToNextStatus() {
        ActionInstance current = new ActionInstance(
                "act-1",
                "order-cancel-flow",
                "order:1",
                ActionStatus.DISPATCHING,
                0,
                2,
                Map.of(),
                null,
                null,
                1,
                Instant.parse("2026-06-26T09:00:00Z"),
                Instant.parse("2026-06-26T09:01:00Z")
        );

        ActionTransitionResult result = ActionStateMachine.apply(
                current,
                ActionTransitionEvent.STEP_SUCCEEDED,
                ActionTransitionContext.atNextStep(1, Instant.parse("2026-06-26T09:02:00Z"))
        );

        assertThat(result.toStatus()).isEqualTo(ActionStatus.DISPATCHING);
        assertThat(result.actionInstance().currentStepIndex()).isEqualTo(1);
    }

    @Test
    void shouldRejectIllegalEventWithExplicitError() {
        ActionInstance current = new ActionInstance(
                "act-1",
                "order-cancel-flow",
                "order:1",
                ActionStatus.SUCCESS,
                1,
                1,
                Map.of(),
                null,
                null,
                1,
                Instant.parse("2026-06-26T09:00:00Z"),
                Instant.parse("2026-06-26T09:01:00Z")
        );

        assertThatThrownBy(() -> ActionStateMachine.apply(
                current,
                ActionTransitionEvent.MANUAL_CANCEL_REQUESTED,
                ActionTransitionContext.atCurrentStep(1, Instant.parse("2026-06-26T09:02:00Z"))
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUCCESS / MANUAL_CANCEL_REQUESTED");
    }
}
