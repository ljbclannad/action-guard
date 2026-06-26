package io.github.actionguard.core.model;

import java.util.EnumSet;

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
        if (nextStatus == null) {
            return false;
        }
        if (this == nextStatus) {
            return true;
        }
        return switch (this) {
            case NEW -> EnumSet.of(DISPATCHING, RETRYING, FAILED).contains(nextStatus);
            case DISPATCHING -> EnumSet.of(RETRYING, SUCCESS, FAILED).contains(nextStatus);
            case RETRYING -> EnumSet.of(DISPATCHING, SUCCESS, FAILED).contains(nextStatus);
            case SUCCESS, FAILED, DEAD, COMPENSATING, COMPENSATED, IGNORED -> false;
        };
    }
}
