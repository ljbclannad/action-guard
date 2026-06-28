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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class RabbitMqActionExecutionMessageProducer implements ActionExecutionMessageProducer {

    private static final String MESSAGE_KEY_HEADER = "x-action-guard-message-key";
    private static final String OUTBOX_ID_HEADER = "x-action-guard-outbox-id";
    private static final String ACTION_INSTANCE_ID_HEADER = "x-action-guard-action-instance-id";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final ActionExecutionMessageFactory messageFactory;
    private final ActionGuardRabbitMqProperties properties;

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
    }

    @Override
    public void publish(ActionOutbox outbox) {
        waitUntilAvailable(outbox);
        ActionExecutionMessage executionMessage = messageFactory.create(outbox);
        rabbitTemplate.send(properties.getExchange(), routingKey(executionMessage), toAmqpMessage(executionMessage));
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

    private void waitUntilAvailable(ActionOutbox outbox) {
        long delayMillis = Duration.between(Instant.now(), outbox.availableAt()).toMillis();
        if (delayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to publish delayed action execution message", ex);
        }
    }
}
