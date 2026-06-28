package io.github.actionguard.adapter.rabbitmq;

import io.github.actionguard.core.model.ActionConsumeDisposition;

public record RabbitMqConsumeDecision(
        ActionConsumeDisposition disposition,
        String reason
) {

    public static RabbitMqConsumeDecision ack() {
        return new RabbitMqConsumeDecision(ActionConsumeDisposition.ACK, null);
    }

    public static RabbitMqConsumeDecision retry(String reason) {
        return new RabbitMqConsumeDecision(ActionConsumeDisposition.RETRY, reason);
    }

    public static RabbitMqConsumeDecision deadLetter(String reason) {
        return new RabbitMqConsumeDecision(ActionConsumeDisposition.DEAD_LETTER, reason);
    }
}
