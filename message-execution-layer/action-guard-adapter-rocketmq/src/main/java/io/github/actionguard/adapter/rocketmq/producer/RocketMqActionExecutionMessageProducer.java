package io.github.actionguard.adapter.rocketmq.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.actionguard.adapter.rocketmq.config.ActionGuardRocketMqProperties;
import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageFactory;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 将 Outbox 转成持久化 RocketMQ 消息，并同步确认 Broker 已接收。
 */
public class RocketMqActionExecutionMessageProducer implements ActionExecutionMessageProducer {

    private static final String MESSAGE_KEY_PROPERTY = "actionGuardMessageKey";
    private static final String OUTBOX_ID_PROPERTY = "actionGuardOutboxId";
    private static final String ACTION_INSTANCE_ID_PROPERTY = "actionGuardActionInstanceId";

    private final DefaultMQProducer producer;
    private final ObjectMapper objectMapper;
    private final ActionExecutionMessageFactory messageFactory;
    private final ActionGuardRocketMqProperties properties;
    private final AtomicBoolean started = new AtomicBoolean();

    public RocketMqActionExecutionMessageProducer(
            DefaultMQProducer producer,
            ObjectMapper objectMapper,
            ActionExecutionMessageFactory messageFactory,
            ActionGuardRocketMqProperties properties
    ) {
        this.producer = Objects.requireNonNull(producer, "producer must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.messageFactory = Objects.requireNonNull(messageFactory, "messageFactory must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public void publish(ActionOutbox outbox) {
        ActionExecutionMessage executionMessage = messageFactory.create(outbox);
        try {
            ensureStarted();
            SendResult result = producer.send(toRocketMqMessage(executionMessage), properties.getSendTimeout().toMillis());
            if (result.getSendStatus() != SendStatus.SEND_OK) {
                throw new IllegalStateException("RocketMQ did not accept action execution message: " + result.getSendStatus());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending action execution message to RocketMQ", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to send action execution message to RocketMQ", ex);
        }
    }

    public void shutdown() {
        if (started.compareAndSet(true, false)) {
            producer.shutdown();
        }
    }

    private synchronized void ensureStarted() throws Exception {
        if (started.compareAndSet(false, true)) {
            try {
                producer.start();
            } catch (Exception ex) {
                started.set(false);
                throw ex;
            }
        }
    }

    private Message toRocketMqMessage(ActionExecutionMessage executionMessage) {
        Message message = new Message(properties.getTopic(), serialize(executionMessage));
        message.setKeys(executionMessage.messageId());
        message.putUserProperty(MESSAGE_KEY_PROPERTY, executionMessage.messageKey());
        message.putUserProperty(OUTBOX_ID_PROPERTY, executionMessage.outboxId());
        message.putUserProperty(ACTION_INSTANCE_ID_PROPERTY, executionMessage.actionInstanceId());
        return message;
    }

    private byte[] serialize(ActionExecutionMessage message) {
        try {
            return objectMapper.writeValueAsString(message).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize action execution message", ex);
        }
    }
}
