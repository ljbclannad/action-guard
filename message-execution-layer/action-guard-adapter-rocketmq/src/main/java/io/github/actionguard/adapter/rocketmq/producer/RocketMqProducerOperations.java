package io.github.actionguard.adapter.rocketmq.producer;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

import java.time.Duration;

/**
 * RocketMQ 生产者的公开 API 生命周期操作。
 */
public final class RocketMqProducerOperations {

    private static final long PRODUCER_REGISTRATION_RETRY_INTERVAL_MILLIS = 200L;

    private RocketMqProducerOperations() {
    }

    /**
     * 启动生产者并预加载目标 Topic 的路由。这里刻意不访问 RocketMQ 的内部实现；
     * 客户端会自行按其公开生命周期向 Broker 发送心跳完成 producer group 注册。
     */
    public static void startAndLoadTopicRoute(DefaultMQProducer producer, String topic) throws Exception {
        producer.start();
        if (producer.fetchPublishMessageQueues(topic).isEmpty()) {
            throw new IllegalStateException("RocketMQ 未返回 Topic 路由: " + topic);
        }
    }

    /**
     * 仅处理新建 producer group 的极短注册窗口：Broker 尚未收到客户端首个心跳时会返回
     * {@code producer group ... not exist}。该错误表示消息未被接收，因此可在给定时限内重试。
     * 其他 Broker 错误一律原样抛出，避免把业务发送失败误判为可重试的初始化问题。
     */
    public static SendResult sendAfterProducerRegistration(
            DefaultMQProducer producer,
            Message message,
            Duration timeout
    ) throws Exception {
        long timeoutMillis = timeout.toMillis();
        long deadlineNanos = System.nanoTime() + Duration.ofMillis(timeoutMillis).toNanos();
        while (true) {
            try {
                return producer.send(message, timeoutMillis);
            } catch (MQBrokerException ex) {
                if (!isProducerGroupNotRegistered(ex) || System.nanoTime() >= deadlineNanos) {
                    throw ex;
                }
                Thread.sleep(PRODUCER_REGISTRATION_RETRY_INTERVAL_MILLIS);
            }
        }
    }

    private static boolean isProducerGroupNotRegistered(MQBrokerException exception) {
        String message = exception.getMessage();
        return message != null
                && message.contains("producer group")
                && message.contains("not exist");
    }
}
