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
}
