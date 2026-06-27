package io.github.actionguard.api.runtime;

public enum ActionAlertType {
    GENERIC,
    RETRIES_EXHAUSTED,
    COMPENSATION_FAILED,
    CONSUME_FAILURE,
    DEAD_LETTER,
    OUTBOX_PUBLISH_FAILED,
    ACTION_STUCK
}
