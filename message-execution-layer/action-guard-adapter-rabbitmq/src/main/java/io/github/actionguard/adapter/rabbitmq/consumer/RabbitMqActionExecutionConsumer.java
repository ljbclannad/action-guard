package io.github.actionguard.adapter.rabbitmq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import io.github.actionguard.adapter.rabbitmq.support.RabbitMqConsumeDecision;
import io.github.actionguard.adapter.rabbitmq.support.RabbitMqConsumeStrategy;
import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.core.model.ActionConsumeDisposition;
import io.github.actionguard.core.repository.ActionConsumeLogRepository;
import io.github.actionguard.core.runtime.execution.ActionExecutionCallback;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * RabbitMQ 版执行消息消费者。
 *
 * <p>它处在 {@code MQ -> consumer -> callback} 这段链路上：Spring AMQP 监听配置好的 queue，
 * 消息到达后由这里完成反序列化、幂等消费记录、ack/retry/dead-letter 决策，然后把真正的执行控制权
 * 交给 {@link ActionExecutionCallback}。
 *
 * <p>因此它本身不实现 action 状态推进逻辑，而是负责把 RabbitMQ 消费模型适配成
 * Action Guard 的执行回调协议；后续真正调用 step handler 的动作发生在 callback 内部。
 */
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
            // 先将 AMQP body 还原为执行消息；此时无法可靠取得业务标识，不能进入执行回调。
            executionMessage = deserialize(message);
        } catch (IllegalStateException ex) {
            // 非法消息按策略直接死信，并在手动确认模式下显式 reject(requeue=false)，避免无意义地重回队列。
            RabbitMqConsumeDecision decision = consumeStrategy.onDeserializationFailure(ex);
            actionObservabilityService.deadLetter(consumerGroup, null, null, decision.reason());
            applyDecision(message, channel, null, decision);
            return;
        }
        Instant now = clock.instant();
        // 首次投递创建 EXECUTING 记录；FAILED 记录可由本次投递重新抢占，其他已有状态视为重复。
        if (!consumeLogRepository.tryStartConsumption(executionMessage, consumerGroup, now)) {
            // 重复消息不再执行 callback，只更新审计状态并 ACK，防止重复副作用。
            consumeLogRepository.markDuplicateSkipped(executionMessage.messageId(), consumerGroup, now);
            applyDecision(message, channel, executionMessage, consumeStrategy.onDuplicate());
            return;
        }
        try {
            // 当前消费者已取得消费权，才将状态推进和业务 Handler 的执行交给 callback。
            callback.execute(executionMessage);
            // callback 成功后先将消费日志落为 ACKED，再按成功决策确认 RabbitMQ 消息。
            consumeLogRepository.markAcked(executionMessage.messageId(), consumerGroup, clock.instant());
            applyDecision(message, channel, executionMessage, consumeStrategy.onSuccess());
        } catch (RuntimeException ex) {
            // 回调或后续消费日志落库失败时，由策略结合当前 redelivery 次数决定重试或死信。
            RabbitMqConsumeDecision decision = consumeStrategy.onCallbackFailure(message, ex);
            if (decision.disposition() == ActionConsumeDisposition.DEAD_LETTER) {
                // 超过重试上限：记录死信结果和告警，再拒绝消息让 RabbitMQ 按队列策略处理。
                consumeLogRepository.markDeadLettered(executionMessage.messageId(), consumerGroup, clock.instant(), decision.reason());
                actionObservabilityService.deadLetter(consumerGroup, executionMessage.actionInstanceId(), executionMessage.messageId(), decision.reason());
            } else {
                // 仍可重试：保留失败记录和原因，再 nack(requeue=true) 交回 RabbitMQ 重新投递。
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
            // 手动确认当前 deliveryTag，RabbitMQ 可从队列中移除该投递。
            channel.basicAck(deliveryTag, false);
            return;
        }
        if (decision.disposition() == ActionConsumeDisposition.RETRY) {
            // 拒绝当前投递并要求重新入队；下次投递仍会先经过消费日志抢占。
            channel.basicNack(deliveryTag, false, true);
            return;
        }
        // 不重新入队；是否进入死信交换机由 RabbitMQ 队列的 DLX 配置决定。
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
