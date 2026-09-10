package io.github.actionguard.adapter.rocketmq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.actionguard.adapter.rocketmq.config.ActionGuardRocketMqProperties;
import io.github.actionguard.adapter.rocketmq.support.RocketMqConsumeStrategy;
import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.core.model.ActionConsumeStatus;
import io.github.actionguard.core.repository.InMemoryActionConsumeLogRepository;
import io.github.actionguard.core.runtime.execution.ActionExecutionCallback;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RocketMqActionExecutionConsumerTest {

    @Test
    void shouldAcknowledgeDuplicateWithoutCallingCallbackAgain() throws Exception {
        AtomicInteger invocations = new AtomicInteger();
        InMemoryActionConsumeLogRepository repository = new InMemoryActionConsumeLogRepository();
        RocketMqActionExecutionConsumer consumer = consumer(repository, message -> invocations.incrementAndGet());
        byte[] body = new ObjectMapper().findAndRegisterModules().writeValueAsBytes(new ActionExecutionMessage(
                "ACTION_EXECUTE:outbox-1", "ACTION_EXECUTE:action-1", "outbox-1", "action-1", "ACTION_EXECUTE",
                Instant.parse("2026-06-26T08:10:00Z")));

        assertThat(consumer.consume(message(body, 0))).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        assertThat(consumer.consume(message(body, 0))).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);

        assertThat(invocations).hasValue(1);
        assertThat(repository.findByMessageId("ACTION_EXECUTE:outbox-1").orElseThrow().consumeStatus())
                .isEqualTo(ActionConsumeStatus.DUPLICATE_SKIPPED);
    }

    @Test
    void shouldRequestBrokerRetryBeforeDeadLetter() throws Exception {
        InMemoryActionConsumeLogRepository repository = new InMemoryActionConsumeLogRepository();
        RocketMqActionExecutionConsumer consumer = consumer(repository, message -> {
            throw new IllegalStateException("failed");
        });
        byte[] body = new ObjectMapper().findAndRegisterModules().writeValueAsBytes(new ActionExecutionMessage(
                "ACTION_EXECUTE:outbox-2", "ACTION_EXECUTE:action-2", "outbox-2", "action-2", "ACTION_EXECUTE",
                Instant.parse("2026-06-26T08:10:00Z")));

        assertThat(consumer.consume(message(body, 0))).isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER);
        assertThat(consumer.consume(message(body, 1))).isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER);

        assertThat(repository.findByMessageId("ACTION_EXECUTE:outbox-2").orElseThrow().consumeStatus())
                .isEqualTo(ActionConsumeStatus.DEAD_LETTERED);
    }

    private RocketMqActionExecutionConsumer consumer(InMemoryActionConsumeLogRepository repository,
                                                     ActionExecutionCallback callback) {
        ActionGuardRocketMqProperties properties = new ActionGuardRocketMqProperties();
        properties.setNameServer("localhost:9876");
        properties.setMaxRedeliveries(1);
        Clock clock = Clock.fixed(Instant.parse("2026-06-26T08:21:00Z"), ZoneOffset.UTC);
        return new RocketMqActionExecutionConsumer(new ObjectMapper().findAndRegisterModules(), repository, callback,
                properties, clock, new RocketMqConsumeStrategy(1),
                new ActionObservabilityService(Optional.empty(), Optional.empty(), clock));
    }

    private MessageExt message(byte[] body, int reconsumeTimes) {
        MessageExt message = new MessageExt();
        message.setBody(body);
        message.setReconsumeTimes(reconsumeTimes);
        return message;
    }
}
