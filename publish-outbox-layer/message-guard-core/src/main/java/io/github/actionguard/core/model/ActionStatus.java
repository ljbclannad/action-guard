package io.github.actionguard.core.model;

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
}
