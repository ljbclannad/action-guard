package io.github.actionguard.core.model;

/**
 * 独立告警 Outbox 的投递状态。
 */
public enum ActionAlertOutboxStatus {
    NEW,
    CLAIMED,
    DONE,
    DEAD
}
