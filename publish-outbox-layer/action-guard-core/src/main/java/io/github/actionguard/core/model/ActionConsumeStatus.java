package io.github.actionguard.core.model;

public enum ActionConsumeStatus {
    RECEIVED,
    EXECUTING,
    ACKED,
    DUPLICATE_SKIPPED,
    FAILED,
    DEAD_LETTERED
}
