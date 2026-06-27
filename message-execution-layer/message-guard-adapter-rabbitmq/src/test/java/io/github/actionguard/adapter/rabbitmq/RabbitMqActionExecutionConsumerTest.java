package io.github.actionguard.adapter.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.api.runtime.ActionAlertEvent;
import io.github.actionguard.api.spi.ActionAlertPublisher;
import io.github.actionguard.core.model.ActionConsumeStatus;
import io.github.actionguard.core.repository.InMemoryActionConsumeLogRepository;
import io.github.actionguard.core.runtime.ActionObservabilityService;
import io.github.actionguard.core.runtime.ActionExecutionCallback;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
class RabbitMqActionExecutionConsumerTest {

    @Test
    void shouldDeserializeMessageAndInvokeRuntimeCallback() throws Exception {
        CapturingCallback callback = new CapturingCallback();
        InMemoryActionConsumeLogRepository consumeLogRepository = new InMemoryActionConsumeLogRepository();
        RabbitMqActionExecutionConsumer consumer = new RabbitMqActionExecutionConsumer(
                new ObjectMapper().findAndRegisterModules(),
                consumeLogRepository,
                callback,
                "rabbitmq-main",
                Clock.fixed(Instant.parse("2026-06-26T08:21:00Z"), ZoneOffset.UTC),
                new RabbitMqConsumeStrategy(1)
        );
        byte[] payload = new ObjectMapper().findAndRegisterModules().writeValueAsBytes(new ActionExecutionMessage(
                "ACTION_EXECUTE:outbox-1",
                "ACTION_EXECUTE:action-1",
                "outbox-1",
                "action-1",
                "ACTION_EXECUTE",
                Instant.parse("2026-06-26T08:20:00Z")
        ));

        Message message = MessageBuilder.withBody(payload)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryTag(1L)
                .build();

        RecordingChannel channel = new RecordingChannel();
        consumer.consume(message, channel.proxy());

        assertThat(channel.acks).containsExactly(1L);
        assertThat(channel.nacks).isEmpty();
        assertThat(channel.rejects).isEmpty();
        assertThat(callback.received()).hasSize(1);
        assertThat(callback.received().get(0).messageId()).isEqualTo("ACTION_EXECUTE:outbox-1");
        assertThat(callback.received().get(0).actionInstanceId()).isEqualTo("action-1");
        assertThat(consumeLogRepository.findByMessageId("ACTION_EXECUTE:outbox-1")).isPresent();
        assertThat(consumeLogRepository.findByMessageId("ACTION_EXECUTE:outbox-1").orElseThrow().consumeStatus())
                .isEqualTo(ActionConsumeStatus.ACKED);
    }

    @Test
    void shouldRejectInvalidJsonWithoutRequeue() throws Exception {
        CapturingCallback callback = new CapturingCallback();
        InMemoryActionConsumeLogRepository consumeLogRepository = new InMemoryActionConsumeLogRepository();
        RabbitMqActionExecutionConsumer consumer = new RabbitMqActionExecutionConsumer(
                new ObjectMapper().findAndRegisterModules(),
                consumeLogRepository,
                callback,
                "rabbitmq-main",
                Clock.fixed(Instant.parse("2026-06-26T08:21:00Z"), ZoneOffset.UTC),
                new RabbitMqConsumeStrategy(1)
        );
        Message message = MessageBuilder.withBody("not-json".getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryTag(2L)
                .build();
        RecordingChannel channel = new RecordingChannel();
        consumer.consume(message, channel.proxy());

        assertThat(channel.rejects).containsExactly(2L);
        assertThat(channel.acks).isEmpty();
        assertThat(channel.nacks).isEmpty();
        assertThat(callback.received()).isEmpty();
    }

    @Test
    void shouldSkipDuplicateMessageWithoutInvokingCallbackTwice() throws Exception {
        CapturingCallback callback = new CapturingCallback();
        InMemoryActionConsumeLogRepository consumeLogRepository = new InMemoryActionConsumeLogRepository();
        RabbitMqActionExecutionConsumer consumer = new RabbitMqActionExecutionConsumer(
                new ObjectMapper().findAndRegisterModules(),
                consumeLogRepository,
                callback,
                "rabbitmq-main",
                Clock.fixed(Instant.parse("2026-06-26T08:21:00Z"), ZoneOffset.UTC),
                new RabbitMqConsumeStrategy(1)
        );
        byte[] payload = new ObjectMapper().findAndRegisterModules().writeValueAsBytes(new ActionExecutionMessage(
                "ACTION_EXECUTE:outbox-1",
                "ACTION_EXECUTE:action-1",
                "outbox-1",
                "action-1",
                "ACTION_EXECUTE",
                Instant.parse("2026-06-26T08:20:00Z")
        ));
        Message message = MessageBuilder.withBody(payload)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryTag(3L)
                .build();
        RecordingChannel firstChannel = new RecordingChannel();
        RecordingChannel secondChannel = new RecordingChannel();

        consumer.consume(message, firstChannel.proxy());
        consumer.consume(message, secondChannel.proxy());

        assertThat(callback.received()).hasSize(1);
        assertThat(firstChannel.acks).containsExactly(3L);
        assertThat(secondChannel.acks).containsExactly(3L);
        assertThat(consumeLogRepository.findByMessageId("ACTION_EXECUTE:outbox-1")).isPresent();
        assertThat(consumeLogRepository.findByMessageId("ACTION_EXECUTE:outbox-1").orElseThrow().consumeStatus())
                .isEqualTo(ActionConsumeStatus.DUPLICATE_SKIPPED);
        assertThat(consumeLogRepository.findByMessageId("ACTION_EXECUTE:outbox-1").orElseThrow().attemptCount()).isEqualTo(2);
    }

    @Test
    void shouldNackForRetryWhenCallbackFailsBeforeRetryLimit() throws Exception {
        FailingCallback callback = new FailingCallback();
        InMemoryActionConsumeLogRepository consumeLogRepository = new InMemoryActionConsumeLogRepository();
        RabbitMqActionExecutionConsumer consumer = new RabbitMqActionExecutionConsumer(
                new ObjectMapper().findAndRegisterModules(),
                consumeLogRepository,
                callback,
                "rabbitmq-main",
                Clock.fixed(Instant.parse("2026-06-26T08:21:00Z"), ZoneOffset.UTC),
                new RabbitMqConsumeStrategy(1)
        );
        byte[] payload = new ObjectMapper().findAndRegisterModules().writeValueAsBytes(new ActionExecutionMessage(
                "ACTION_EXECUTE:outbox-2",
                "ACTION_EXECUTE:action-2",
                "outbox-2",
                "action-2",
                "ACTION_EXECUTE",
                Instant.parse("2026-06-26T08:20:00Z")
        ));
        Message message = MessageBuilder.withBody(payload)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryTag(4L)
                .build();
        RecordingChannel channel = new RecordingChannel();

        consumer.consume(message, channel.proxy());

        assertThat(channel.nacks).containsExactly(4L);
        assertThat(channel.acks).isEmpty();
        assertThat(channel.rejects).isEmpty();
        assertThat(consumeLogRepository.findByMessageId("ACTION_EXECUTE:outbox-2")).isPresent();
        assertThat(consumeLogRepository.findByMessageId("ACTION_EXECUTE:outbox-2").orElseThrow().consumeStatus())
                .isEqualTo(ActionConsumeStatus.FAILED);
    }

    @Test
    void shouldRejectToDeadLetterWhenCallbackFailsAfterRetryLimit() throws Exception {
        FailingCallback callback = new FailingCallback();
        InMemoryActionConsumeLogRepository consumeLogRepository = new InMemoryActionConsumeLogRepository();
        CapturingAlertPublisher alertPublisher = new CapturingAlertPublisher();
        RabbitMqActionExecutionConsumer consumer = new RabbitMqActionExecutionConsumer(
                new ObjectMapper().findAndRegisterModules(),
                consumeLogRepository,
                callback,
                "rabbitmq-main",
                Clock.fixed(Instant.parse("2026-06-26T08:21:00Z"), ZoneOffset.UTC),
                new RabbitMqConsumeStrategy(1),
                new ActionObservabilityService(Optional.of(alertPublisher), Optional.empty(), Clock.fixed(Instant.parse("2026-06-26T08:21:00Z"), ZoneOffset.UTC))
        );
        byte[] payload = new ObjectMapper().findAndRegisterModules().writeValueAsBytes(new ActionExecutionMessage(
                "ACTION_EXECUTE:outbox-3",
                "ACTION_EXECUTE:action-3",
                "outbox-3",
                "action-3",
                "ACTION_EXECUTE",
                Instant.parse("2026-06-26T08:20:00Z")
        ));
        Message message = MessageBuilder.withBody(payload)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryTag(5L)
                .setHeader("x-delivery-count", 2)
                .build();
        RecordingChannel channel = new RecordingChannel();

        consumer.consume(message, channel.proxy());

        assertThat(channel.rejects).containsExactly(5L);
        assertThat(channel.acks).isEmpty();
        assertThat(channel.nacks).isEmpty();
        assertThat(consumeLogRepository.findByMessageId("ACTION_EXECUTE:outbox-3")).isPresent();
        assertThat(consumeLogRepository.findByMessageId("ACTION_EXECUTE:outbox-3").orElseThrow().consumeStatus())
                .isEqualTo(ActionConsumeStatus.DEAD_LETTERED);
        assertThat(alertPublisher.events).hasSize(1);
        assertThat(alertPublisher.events.get(0).type().name()).isEqualTo("DEAD_LETTER");
    }

    @Test
    void shouldMarkConsumeLogAckedAfterRetryMessageEventuallySucceeds() throws Exception {
        FlakyCallback callback = new FlakyCallback();
        InMemoryActionConsumeLogRepository consumeLogRepository = new InMemoryActionConsumeLogRepository();
        RabbitMqActionExecutionConsumer consumer = new RabbitMqActionExecutionConsumer(
                new ObjectMapper().findAndRegisterModules(),
                consumeLogRepository,
                callback,
                "rabbitmq-main",
                Clock.fixed(Instant.parse("2026-06-26T08:21:00Z"), ZoneOffset.UTC),
                new RabbitMqConsumeStrategy(3)
        );
        byte[] payload = new ObjectMapper().findAndRegisterModules().writeValueAsBytes(new ActionExecutionMessage(
                "ACTION_EXECUTE:outbox-4",
                "ACTION_EXECUTE:action-4",
                "outbox-4",
                "action-4",
                "ACTION_EXECUTE",
                Instant.parse("2026-06-26T08:20:00Z")
        ));
        Message firstDelivery = MessageBuilder.withBody(payload)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryTag(6L)
                .build();
        Message secondDelivery = MessageBuilder.withBody(payload)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryTag(7L)
                .setHeader("x-delivery-count", 1)
                .build();
        RecordingChannel firstChannel = new RecordingChannel();
        RecordingChannel secondChannel = new RecordingChannel();

        consumer.consume(firstDelivery, firstChannel.proxy());
        consumer.consume(secondDelivery, secondChannel.proxy());

        assertThat(firstChannel.nacks).containsExactly(6L);
        assertThat(secondChannel.acks).containsExactly(7L);
        assertThat(callback.invocationCount()).isEqualTo(2);
        assertThat(consumeLogRepository.findByMessageId("ACTION_EXECUTE:outbox-4")).isPresent();
        assertThat(consumeLogRepository.findByMessageId("ACTION_EXECUTE:outbox-4").orElseThrow().consumeStatus())
                .isEqualTo(ActionConsumeStatus.ACKED);
        assertThat(consumeLogRepository.findByMessageId("ACTION_EXECUTE:outbox-4").orElseThrow().attemptCount())
                .isEqualTo(2);
    }

    @Test
    void shouldSkipDeadLetteredDuplicateMessageWithoutInvokingCallbackAgain() throws Exception {
        FailingCallback callback = new FailingCallback();
        InMemoryActionConsumeLogRepository consumeLogRepository = new InMemoryActionConsumeLogRepository();
        RabbitMqActionExecutionConsumer consumer = new RabbitMqActionExecutionConsumer(
                new ObjectMapper().findAndRegisterModules(),
                consumeLogRepository,
                callback,
                "rabbitmq-main",
                Clock.fixed(Instant.parse("2026-06-26T08:21:00Z"), ZoneOffset.UTC),
                new RabbitMqConsumeStrategy(1)
        );
        byte[] payload = new ObjectMapper().findAndRegisterModules().writeValueAsBytes(new ActionExecutionMessage(
                "ACTION_EXECUTE:outbox-5",
                "ACTION_EXECUTE:action-5",
                "outbox-5",
                "action-5",
                "ACTION_EXECUTE",
                Instant.parse("2026-06-26T08:20:00Z")
        ));
        Message deadLetterDelivery = MessageBuilder.withBody(payload)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryTag(8L)
                .setHeader("x-delivery-count", 2)
                .build();
        Message duplicateDelivery = MessageBuilder.withBody(payload)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryTag(9L)
                .setHeader("x-delivery-count", 3)
                .build();
        RecordingChannel firstChannel = new RecordingChannel();
        RecordingChannel secondChannel = new RecordingChannel();

        consumer.consume(deadLetterDelivery, firstChannel.proxy());
        consumer.consume(duplicateDelivery, secondChannel.proxy());

        assertThat(firstChannel.rejects).containsExactly(8L);
        assertThat(secondChannel.acks).containsExactly(9L);
        assertThat(callback.invocationCount()).isEqualTo(1);
        assertThat(consumeLogRepository.findByMessageId("ACTION_EXECUTE:outbox-5")).isPresent();
        assertThat(consumeLogRepository.findByMessageId("ACTION_EXECUTE:outbox-5").orElseThrow().consumeStatus())
                .isEqualTo(ActionConsumeStatus.DUPLICATE_SKIPPED);
        assertThat(consumeLogRepository.findByMessageId("ACTION_EXECUTE:outbox-5").orElseThrow().attemptCount())
                .isEqualTo(2);
    }

    private static final class CapturingCallback implements ActionExecutionCallback {
        private final List<ActionExecutionMessage> received = new ArrayList<>();

        @Override
        public void execute(ActionExecutionMessage message) {
            received.add(message);
        }

        private List<ActionExecutionMessage> received() {
            return List.copyOf(received);
        }
    }

    private static final class FailingCallback implements ActionExecutionCallback {

        private int invocationCount;

        @Override
        public void execute(ActionExecutionMessage message) {
            invocationCount++;
            throw new IllegalStateException("callback failed");
        }

        private int invocationCount() {
            return invocationCount;
        }
    }

    private static final class FlakyCallback implements ActionExecutionCallback {
        private int invocationCount;

        @Override
        public void execute(ActionExecutionMessage message) {
            invocationCount++;
            if (invocationCount == 1) {
                throw new IllegalStateException("callback failed once");
            }
        }

        private int invocationCount() {
            return invocationCount;
        }
    }

    private static final class CapturingAlertPublisher implements ActionAlertPublisher {
        private final List<ActionAlertEvent> events = new ArrayList<>();

        @Override
        public void publish(ActionAlertEvent event) {
            events.add(event);
        }
    }

    private static final class RecordingChannel {
        private final List<Long> acks = new ArrayList<>();
        private final List<Long> nacks = new ArrayList<>();
        private final List<Long> rejects = new ArrayList<>();

        private Channel proxy() {
            InvocationHandler handler = (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "basicAck" -> {
                        acks.add((Long) args[0]);
                        yield null;
                    }
                    case "basicNack" -> {
                        nacks.add((Long) args[0]);
                        yield null;
                    }
                    case "basicReject" -> {
                        rejects.add((Long) args[0]);
                        yield null;
                    }
                    case "isOpen" -> true;
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            };
            return (Channel) Proxy.newProxyInstance(
                    Channel.class.getClassLoader(),
                    new Class[]{Channel.class},
                    handler
            );
        }
    }
}
