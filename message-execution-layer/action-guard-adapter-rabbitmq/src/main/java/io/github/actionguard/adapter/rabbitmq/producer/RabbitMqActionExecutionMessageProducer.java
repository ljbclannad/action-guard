package io.github.actionguard.adapter.rabbitmq.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.actionguard.adapter.rabbitmq.config.ActionGuardRabbitMqProperties;
import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageFactory;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.ReturnedMessage;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Objects;

/**
 * RabbitMQ 版执行消息生产者。
 *
 * <p>它处在 {@code publish -> MQ} 这半段链路上：starter 在事务提交后拿到 outbox，
 * 通过 {@link io.github.actionguard.core.runtime.execution.ActionExecutionMessageFactory}
 * 转成 {@link ActionExecutionMessage}，再由这里序列化并发送到 RabbitMQ exchange。
 *
 * <p>发送完成后，这个类的职责就结束了；后续消息路由、消费、回调执行分别由 RabbitMQ、
 * {@code RabbitMqActionExecutionConsumer} 和 {@code ActionExecutionCallback} 接手。
 */
public class RabbitMqActionExecutionMessageProducer implements ActionExecutionMessageProducer {

    private static final String MESSAGE_KEY_HEADER = "x-action-guard-message-key";
    private static final String OUTBOX_ID_HEADER = "x-action-guard-outbox-id";
    private static final String ACTION_INSTANCE_ID_HEADER = "x-action-guard-action-instance-id";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final ActionExecutionMessageFactory messageFactory;
    private final ActionGuardRabbitMqProperties properties;
    private final ConcurrentMap<String, ReturnedMessage> returnedMessages = new ConcurrentHashMap<>();

    public RabbitMqActionExecutionMessageProducer(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            ActionExecutionMessageFactory messageFactory,
            ActionGuardRabbitMqProperties properties
    ) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.messageFactory = Objects.requireNonNull(messageFactory, "messageFactory must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.rabbitTemplate.setMandatory(true);
        this.rabbitTemplate.setReturnsCallback(returned -> returnedMessages.put(
                returned.getMessage().getMessageProperties().getMessageId(), returned
        ));
    }

    @Override
    public void publish(ActionOutbox outbox) {
        ActionExecutionMessage executionMessage = messageFactory.create(outbox);
        returnedMessages.remove(executionMessage.messageId());
        rabbitTemplate.invoke(operations -> {
            operations.send(properties.getExchange(), routingKey(executionMessage), toAmqpMessage(executionMessage));
            if (!operations.waitForConfirms(properties.getConfirmTimeout().toMillis())) {
                throw new IllegalStateException("RabbitMQ did not confirm action execution message");
            }
            return null;
        });
        ReturnedMessage returned = returnedMessages.remove(executionMessage.messageId());
        if (returned != null) {
            throw new IllegalStateException("RabbitMQ returned unroutable action execution message: " + returned.getReplyText());
        }
    }

    private String routingKey(ActionExecutionMessage message) {
        return properties.getRoutingKeyPrefix() + "." + message.actionInstanceId();
    }

    private Message toAmqpMessage(ActionExecutionMessage message) {
        byte[] body = serialize(message);
        return MessageBuilder.withBody(body)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(message.messageId())
                .setHeader(MESSAGE_KEY_HEADER, message.messageKey())
                .setHeader(OUTBOX_ID_HEADER, message.outboxId())
                .setHeader(ACTION_INSTANCE_ID_HEADER, message.actionInstanceId())
                .build();
    }

    private byte[] serialize(ActionExecutionMessage message) {
        try {
            return objectMapper.writeValueAsBytes(message);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize action execution message", ex);
        }
    }

}
