package io.github.actionguard.core.model;

import io.github.actionguard.core.runtime.state.ActionStateMachine;

public enum ActionStatus {
    NEW(false),
    DISPATCHING(false),
    SUCCESS(true),
    RETRYING(false),
    FAILED(false),
    DEAD(true),
    COMPENSATING(false),
    COMPENSATED(true),
    IGNORED(true);

    private final boolean terminal;

    ActionStatus(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public boolean canTransitionTo(ActionStatus nextStatus) {
        return ActionStateMachine.canTransition(this, nextStatus);
    }
}
