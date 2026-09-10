package io.github.actionguard.adapter.rocketmq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.actionguard.adapter.rocketmq.config.ActionGuardRocketMqProperties;
import io.github.actionguard.adapter.rocketmq.support.RocketMqConsumeDecision;
import io.github.actionguard.adapter.rocketmq.support.RocketMqConsumeStrategy;
import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.core.model.ActionConsumeDisposition;
import io.github.actionguard.core.repository.ActionConsumeLogRepository;
import io.github.actionguard.core.runtime.execution.ActionExecutionCallback;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.context.SmartLifecycle;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * RocketMQ 版执行消息消费者。
 *
 * <p>消费日志和 Broker 确认不是原子操作，进程在两者之间退出时会出现重复投递；
 * {@link ActionConsumeLogRepository} 负责在重复投递时阻止重复副作用。
 */
public class RocketMqActionExecutionConsumer implements SmartLifecycle {

    private final ObjectMapper objectMapper;
    private final ActionConsumeLogRepository consumeLogRepository;
    private final ActionExecutionCallback callback;
    private final ActionGuardRocketMqProperties properties;
    private final Clock clock;
    private final RocketMqConsumeStrategy consumeStrategy;
    private final ActionObservabilityService observabilityService;
    private final DefaultMQPushConsumer consumer;
    private volatile boolean running;

    public RocketMqActionExecutionConsumer(
            ObjectMapper objectMapper,
            ActionConsumeLogRepository consumeLogRepository,
            ActionExecutionCallback callback,
            ActionGuardRocketMqProperties properties,
            Clock clock,
            RocketMqConsumeStrategy consumeStrategy,
            ActionObservabilityService observabilityService
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.consumeLogRepository = Objects.requireNonNull(consumeLogRepository, "consumeLogRepository must not be null");
        this.callback = Objects.requireNonNull(callback, "callback must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.consumeStrategy = Objects.requireNonNull(consumeStrategy, "consumeStrategy must not be null");
        this.observabilityService = Objects.requireNonNull(observabilityService, "observabilityService must not be null");
        this.consumer = new DefaultMQPushConsumer(properties.getConsumerGroup());
        this.consumer.setNamesrvAddr(properties.getNameServer());
        this.consumer.setMaxReconsumeTimes(properties.getMaxRedeliveries());
        this.consumer.registerMessageListener((MessageListenerConcurrently) this::consumeBatch);
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        try {
            consumer.subscribe(properties.getTopic(), "*");
            consumer.start();
            running = true;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to start RocketMQ action execution consumer", ex);
        }
    }

    @Override
    public void stop() {
        if (running) {
            consumer.shutdown();
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    ConsumeConcurrentlyStatus consume(MessageExt message) {
        ActionExecutionMessage executionMessage;
        try {
            executionMessage = objectMapper.readValue(message.getBody(), ActionExecutionMessage.class);
        } catch (Exception ex) {
            RocketMqConsumeDecision decision = consumeStrategy.onFailure(message, ex);
            if (decision.disposition() == ActionConsumeDisposition.DEAD_LETTER) {
                observabilityService.deadLetter(properties.getConsumerGroup(), null, null, decision.reason());
            }
            return toRocketMqStatus(decision);
        }
        Instant now = clock.instant();
        if (!consumeLogRepository.tryStartConsumption(executionMessage, properties.getConsumerGroup(), now)) {
            consumeLogRepository.markDuplicateSkipped(executionMessage.messageId(), properties.getConsumerGroup(), now);
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
        try {
            callback.execute(executionMessage);
            consumeLogRepository.markAcked(executionMessage.messageId(), properties.getConsumerGroup(), clock.instant());
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (RuntimeException ex) {
            RocketMqConsumeDecision decision = consumeStrategy.onFailure(message, ex);
            if (decision.disposition() == ActionConsumeDisposition.DEAD_LETTER) {
                consumeLogRepository.markDeadLettered(executionMessage.messageId(), properties.getConsumerGroup(), clock.instant(), decision.reason());
                observabilityService.deadLetter(properties.getConsumerGroup(), executionMessage.actionInstanceId(), executionMessage.messageId(), decision.reason());
            } else {
                consumeLogRepository.markFailed(executionMessage.messageId(), properties.getConsumerGroup(), clock.instant(), decision.reason());
                observabilityService.consumeFailure(properties.getConsumerGroup(), executionMessage.actionInstanceId(), executionMessage.messageId(), decision.reason());
            }
            return toRocketMqStatus(decision);
        }
    }

    private ConsumeConcurrentlyStatus consumeBatch(List<MessageExt> messages, ConsumeConcurrentlyContext context) {
        for (MessageExt message : messages) {
            if (consume(message) == ConsumeConcurrentlyStatus.RECONSUME_LATER) {
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    private ConsumeConcurrentlyStatus toRocketMqStatus(RocketMqConsumeDecision decision) {
        return decision.disposition() == ActionConsumeDisposition.ACK
                ? ConsumeConcurrentlyStatus.CONSUME_SUCCESS
                : ConsumeConcurrentlyStatus.RECONSUME_LATER;
    }
}
