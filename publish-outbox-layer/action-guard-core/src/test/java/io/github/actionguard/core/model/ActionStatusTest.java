package io.github.actionguard.core.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActionStatusTest {

    @Test
    void shouldTreatSuccessAndIgnoredAsTerminalStates() {
        assertThat(ActionStatus.SUCCESS.isTerminal()).isTrue();
        assertThat(ActionStatus.IGNORED.isTerminal()).isTrue();
        assertThat(ActionStatus.DISPATCHING.isTerminal()).isFalse();
    }

    @Test
    void shouldDefineP0ActionStatusStateMachine() {
        assertThat(ActionStatus.NEW.canTransitionTo(ActionStatus.DISPATCHING)).isTrue();
        assertThat(ActionStatus.NEW.canTransitionTo(ActionStatus.SUCCESS)).isTrue();
        assertThat(ActionStatus.NEW.canTransitionTo(ActionStatus.RETRYING)).isTrue();
        assertThat(ActionStatus.NEW.canTransitionTo(ActionStatus.FAILED)).isTrue();
        assertThat(ActionStatus.NEW.canTransitionTo(ActionStatus.IGNORED)).isTrue();

        assertThat(ActionStatus.DISPATCHING.canTransitionTo(ActionStatus.RETRYING)).isTrue();
        assertThat(ActionStatus.DISPATCHING.canTransitionTo(ActionStatus.SUCCESS)).isTrue();
        assertThat(ActionStatus.DISPATCHING.canTransitionTo(ActionStatus.FAILED)).isTrue();
        assertThat(ActionStatus.DISPATCHING.canTransitionTo(ActionStatus.IGNORED)).isTrue();

        assertThat(ActionStatus.RETRYING.canTransitionTo(ActionStatus.DISPATCHING)).isTrue();
        assertThat(ActionStatus.RETRYING.canTransitionTo(ActionStatus.SUCCESS)).isTrue();
        assertThat(ActionStatus.RETRYING.canTransitionTo(ActionStatus.FAILED)).isTrue();

        assertThat(ActionStatus.FAILED.canTransitionTo(ActionStatus.COMPENSATING)).isTrue();
        assertThat(ActionStatus.DEAD.canTransitionTo(ActionStatus.COMPENSATING)).isTrue();
        assertThat(ActionStatus.COMPENSATING.canTransitionTo(ActionStatus.COMPENSATED)).isTrue();
        assertThat(ActionStatus.COMPENSATING.canTransitionTo(ActionStatus.DEAD)).isTrue();

        assertThat(ActionStatus.SUCCESS.canTransitionTo(ActionStatus.RETRYING)).isFalse();
        assertThat(ActionStatus.SUCCESS.canTransitionTo(ActionStatus.DISPATCHING)).isFalse();
        assertThat(ActionStatus.FAILED.canTransitionTo(ActionStatus.DISPATCHING)).isFalse();
        assertThat(ActionStatus.FAILED.canTransitionTo(ActionStatus.RETRYING)).isFalse();
    }
}
