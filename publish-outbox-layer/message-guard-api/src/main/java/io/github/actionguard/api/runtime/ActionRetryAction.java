package io.github.actionguard.api.runtime;

public enum ActionRetryAction {
    IMMEDIATE_RETRY,
    DELAY_RETRY,
    DEAD,
    COMPENSATE
}
