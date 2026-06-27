package io.github.actionguard.adapter.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.core.model.ActionConsumeDisposition;
import io.github.actionguard.core.repository.ActionConsumeLogRepository;
import io.github.actionguard.core.runtime.ActionObservabilityService;
import io.github.actionguard.core.runtime.ActionExecutionCallback;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class RabbitMqActionExecutionConsumer {

    private final ObjectMapper objectMapper;
    private final ActionConsumeLogRepository consumeLogRepository;
    private final ActionExecutionCallback callback;
    private final String consumerGroup;
    private final Clock clock;
    private final RabbitMqConsumeStrategy consumeStrategy;
    private final ActionObservabilityService actionObservabilityService;

    public RabbitMqActionExecutionConsumer(
            ObjectMapper objectMapper,
            ActionConsumeLogRepository consumeLogRepository,
            ActionExecutionCallback callback,
            String consumerGroup,
            Clock clock,
            RabbitMqConsumeStrategy consumeStrategy,
            ActionObservabilityService actionObservabilityService
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.consumeLogRepository = Objects.requireNonNull(consumeLogRepository, "consumeLogRepository must not be null");
        this.callback = Objects.requireNonNull(callback, "callback must not be null");
        this.consumerGroup = Objects.requireNonNull(consumerGroup, "consumerGroup must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.consumeStrategy = Objects.requireNonNull(consumeStrategy, "consumeStrategy must not be null");
        this.actionObservabilityService = Objects.requireNonNull(actionObservabilityService, "actionObservabilityService must not be null");
    }

    public RabbitMqActionExecutionConsumer(
            ObjectMapper objectMapper,
            ActionConsumeLogRepository consumeLogRepository,
            ActionExecutionCallback callback,
            String consumerGroup,
            Clock clock,
            RabbitMqConsumeStrategy consumeStrategy
    ) {
        this(
                objectMapper,
                consumeLogRepository,
                callback,
                consumerGroup,
                clock,
                consumeStrategy,
                new ActionObservabilityService(java.util.Optional.empty(), java.util.Optional.empty(), clock)
        );
    }

    @RabbitListener(queues = "${action.guard.rabbitmq.queue:action.guard.execute.queue}", ackMode = "MANUAL")
    public void consume(Message message, Channel channel) throws IOException {
        ActionExecutionMessage executionMessage;
        try {
            executionMessage = deserialize(message);
        } catch (IllegalStateException ex) {
            RabbitMqConsumeDecision decision = consumeStrategy.onDeserializationFailure(ex);
            actionObservabilityService.deadLetter(consumerGroup, null, null, decision.reason());
            applyDecision(message, channel, null, decision);
            return;
        }
        Instant now = clock.instant();
        if (!consumeLogRepository.tryStartConsumption(executionMessage, consumerGroup, now)) {
            consumeLogRepository.markDuplicateSkipped(executionMessage.messageId(), consumerGroup, now);
            applyDecision(message, channel, executionMessage, consumeStrategy.onDuplicate());
            return;
        }
        try {
            callback.execute(executionMessage);
            consumeLogRepository.markAcked(executionMessage.messageId(), consumerGroup, clock.instant());
            applyDecision(message, channel, executionMessage, consumeStrategy.onSuccess());
        } catch (RuntimeException ex) {
            RabbitMqConsumeDecision decision = consumeStrategy.onCallbackFailure(message, ex);
            if (decision.disposition() == ActionConsumeDisposition.DEAD_LETTER) {
                consumeLogRepository.markDeadLettered(executionMessage.messageId(), consumerGroup, clock.instant(), decision.reason());
                actionObservabilityService.deadLetter(consumerGroup, executionMessage.actionInstanceId(), executionMessage.messageId(), decision.reason());
            } else {
                consumeLogRepository.markFailed(executionMessage.messageId(), consumerGroup, clock.instant(), decision.reason());
                actionObservabilityService.consumeFailure(consumerGroup, executionMessage.actionInstanceId(), executionMessage.messageId(), decision.reason());
            }
            applyDecision(message, channel, executionMessage, decision);
        }
    }

    private void applyDecision(
            Message message,
            Channel channel,
            ActionExecutionMessage executionMessage,
            RabbitMqConsumeDecision decision
    ) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        if (decision.disposition() == ActionConsumeDisposition.ACK) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        if (decision.disposition() == ActionConsumeDisposition.RETRY) {
            channel.basicNack(deliveryTag, false, true);
            return;
        }
        channel.basicReject(deliveryTag, false);
    }

    private ActionExecutionMessage deserialize(Message message) {
        try {
            return objectMapper.readValue(message.getBody(), ActionExecutionMessage.class);
        } catch (IOException ex) {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            throw new IllegalStateException("Failed to deserialize action execution message: " + payload, ex);
        }
    }
}
