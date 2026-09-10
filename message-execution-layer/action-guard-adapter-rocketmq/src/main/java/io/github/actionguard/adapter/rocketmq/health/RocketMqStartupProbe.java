package io.github.actionguard.adapter.rocketmq.health;

import io.github.actionguard.adapter.rocketmq.config.ActionGuardRocketMqProperties;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 启动期 RocketMQ 端到端探活。
 *
 * <p>探测使用独立 topic 和临时 consumer group，不会进入 Action 执行消费者，因而不会创建或推进任何 Action。
 */
public class RocketMqStartupProbe {

    private static final String PROBE_ID_PROPERTY = "actionGuardProbeId";

    private final ActionGuardRocketMqProperties properties;

    public RocketMqStartupProbe(ActionGuardRocketMqProperties properties) {
        this.properties = properties;
    }

    public void verify() {
        // 先在本地校验配置，避免连接 Broker 后才因非法 topic 得到不直观的服务端错误。
        validateProbeTopic();
        // 每次启动生成唯一标识，消费者只确认自己本次发出的消息，避免历史探测消息造成误判。
        String probeId = UUID.randomUUID().toString();
        // 使用固定的专用 group，既与业务消费组隔离，也避免每次启动产生新的 group 元数据。
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(properties.getConsumerGroup() + "-startup-probe");
        DefaultMQProducer producer = new DefaultMQProducer(properties.getProducerGroup() + "-startup-probe");
        // 只需收到一条匹配消息即可证明“发送 -> Broker 路由 -> 消费”全链路成立。
        CountDownLatch received = new CountDownLatch(1);
        try {
            // 消费者必须先于生产者启动；否则消息可能在订阅建立前被发送，导致探测出现竞态。
            consumer.setNamesrvAddr(properties.getNameServer());
            // 只消费启动后产生的 Probe，避免读取该 Topic 中的历史记录。
            consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
            // Probe 使用独立 Topic，绝不能复用 Action 执行 Topic，以免触发真实业务步骤。
            consumer.subscribe(properties.getStartupProbeTopic(), "*");
            consumer.registerMessageListener((MessageListenerConcurrently) (List<MessageExt> messages, ConsumeConcurrentlyContext context) -> {
                for (MessageExt message : messages) {
                    // 同一批次可能含其他实例或历史 Probe，只确认携带本次 probeId 的那一条。
                    if (probeId.equals(message.getUserProperty(PROBE_ID_PROPERTY))) {
                        received.countDown();
                    }
                }
                // Probe 已在本地处理完毕；无论是否匹配本次 ID 都确认，避免无意义地进入重试队列。
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
            consumer.start();

            // 临时生产者通过同一个 NameServer 发现 Broker，并用同步发送取得 Broker 接收确认。
            producer.setNamesrvAddr(properties.getNameServer());
            producer.start();
            Message message = new Message(properties.getStartupProbeTopic(), "action-guard-startup-probe".getBytes(StandardCharsets.UTF_8));
            // keys 便于在 Dashboard 或 mqadmin 中定位本次启动探测消息。
            message.setKeys(probeId);
            message.putUserProperty(PROBE_ID_PROPERTY, probeId);
            SendResult result = producer.send(message, properties.getStartupProbeTimeout().toMillis());
            if (result.getSendStatus() != SendStatus.SEND_OK) {
                throw new IllegalStateException("RocketMQ 启动探活发送未被 Broker 确认: " + result.getSendStatus());
            }
            // 发送成功只代表 Broker 收到消息；等待回执才能证明消费者也可以正常拉取和处理。
            if (!received.await(properties.getStartupProbeTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("RocketMQ 启动探活超时，未在 " + properties.getStartupProbeTimeout() + " 内收到 Probe 消息");
            }
        } catch (InterruptedException ex) {
            // 保留中断标记，避免吞掉容器关闭或线程池管理发出的中断信号。
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RocketMQ 启动探活被中断", ex);
        } catch (Exception ex) {
            // ApplicationRunner 收到异常会使 Spring Boot 启动失败，阻止不具备 MQ 能力的实例接收流量。
            throw new IllegalStateException("RocketMQ 启动探活失败", ex);
        } finally {
            // Probe 仅用于启动检查，完成后立即释放网络连接和客户端线程，不常驻占用 Broker 资源。
            producer.shutdown();
            consumer.shutdown();
        }
    }

    private void validateProbeTopic() {
        // RocketMQ 不允许点号等字符；与业务 Topic 校验规则保持一致。
        if (properties.getStartupProbeTopic() == null || !properties.getStartupProbeTopic().matches("^[%|a-zA-Z0-9_-]+$")) {
            throw new IllegalStateException("action.guard.rocketmq.startup-probe-topic 包含非法字符");
        }
    }
}
