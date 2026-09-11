package io.github.actionguard.adapter.rocketmq.consumer;

import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;

/** 仅供 demo 集成测试驱动真实 RocketMQ 消费逻辑，不启动客户端生命周期。 */
public final class RocketMqConsumerTestBridge {

    private RocketMqConsumerTestBridge() {
    }

    public static ConsumeConcurrentlyStatus consume(RocketMqActionExecutionConsumer consumer, MessageExt message) {
        return consumer.consume(message);
    }
}
