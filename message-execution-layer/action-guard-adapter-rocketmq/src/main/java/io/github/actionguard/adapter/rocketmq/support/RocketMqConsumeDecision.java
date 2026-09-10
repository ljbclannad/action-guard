package io.github.actionguard.adapter.rocketmq.support;

import io.github.actionguard.core.model.ActionConsumeDisposition;

public record RocketMqConsumeDecision(ActionConsumeDisposition disposition, String reason) {

    public static RocketMqConsumeDecision ack() {
        return new RocketMqConsumeDecision(ActionConsumeDisposition.ACK, null);
    }

    public static RocketMqConsumeDecision retry(String reason) {
        return new RocketMqConsumeDecision(ActionConsumeDisposition.RETRY, reason);
    }

    public static RocketMqConsumeDecision deadLetter(String reason) {
        return new RocketMqConsumeDecision(ActionConsumeDisposition.DEAD_LETTER, reason);
    }
}
