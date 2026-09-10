package io.github.actionguard.adapter.rocketmq.support;

import org.apache.rocketmq.common.message.MessageExt;

/**
 * 将消费结果转换成 RocketMQ 的确认、重试或死信决策。
 */
public class RocketMqConsumeStrategy {

    private final int maxRedeliveries;

    public RocketMqConsumeStrategy(int maxRedeliveries) {
        this.maxRedeliveries = maxRedeliveries;
    }

    public RocketMqConsumeDecision onSuccess() {
        return RocketMqConsumeDecision.ack();
    }

    public RocketMqConsumeDecision onDuplicate() {
        return RocketMqConsumeDecision.ack();
    }

    public RocketMqConsumeDecision onFailure(MessageExt message, Exception ex) {
        if (message.getReconsumeTimes() >= maxRedeliveries) {
            return RocketMqConsumeDecision.deadLetter(ex.getMessage());
        }
        return RocketMqConsumeDecision.retry(ex.getMessage());
    }
}
