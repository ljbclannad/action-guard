package io.github.actionguard.adapter.rabbitmq;

import org.springframework.amqp.core.Message;

public class RabbitMqConsumeStrategy {

    private final int maxRedeliveries;

    public RabbitMqConsumeStrategy(int maxRedeliveries) {
        this.maxRedeliveries = maxRedeliveries;
    }

    public RabbitMqConsumeDecision onSuccess() {
        return RabbitMqConsumeDecision.ack();
    }

    public RabbitMqConsumeDecision onDuplicate() {
        return RabbitMqConsumeDecision.ack();
    }

    public RabbitMqConsumeDecision onDeserializationFailure(Exception ex) {
        return RabbitMqConsumeDecision.deadLetter(ex.getMessage());
    }

    public RabbitMqConsumeDecision onCallbackFailure(Message message, RuntimeException ex) {
        if (redeliveryCount(message) > maxRedeliveries) {
            return RabbitMqConsumeDecision.deadLetter(ex.getMessage());
        }
        return RabbitMqConsumeDecision.retry(ex.getMessage());
    }

    int redeliveryCount(Message message) {
        Object xDeliveryCount = message.getMessageProperties().getHeaders().get("x-delivery-count");
        if (xDeliveryCount instanceof Number number) {
            return number.intValue();
        }
        Boolean redelivered = message.getMessageProperties().getRedelivered();
        return Boolean.TRUE.equals(redelivered) ? 1 : 0;
    }
}
