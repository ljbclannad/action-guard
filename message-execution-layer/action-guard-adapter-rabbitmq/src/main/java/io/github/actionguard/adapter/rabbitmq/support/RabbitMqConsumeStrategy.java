package io.github.actionguard.adapter.rabbitmq.support;

import org.springframework.amqp.core.Message;

/**
 * 将消费结果转换为 RabbitMQ 的 ACK、重试或死信决定。
 *
 * <p>该类不写消费日志，也不直接操作 Channel；消费者根据返回的
 * {@link RabbitMqConsumeDecision} 更新消费记录、发送观测事件，并执行对应的 ACK、NACK 或 Reject。
 * 成功和重复投递均确认消息，反序列化失败直接死信；回调失败时，仅当重投次数严格大于
 * {@code maxRedeliveries} 才进入死信，否则要求 RabbitMQ 重新入队。
 *
 * <p>重投次数优先读取 {@code x-delivery-count} Header；Header 缺失时，{@code redelivered=true}
 * 仅按一次重投处理，无法表达精确的累计投递次数。
 */
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
