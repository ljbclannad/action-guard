package io.github.actionguard.demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.actionguard.adapter.rocketmq.config.ActionGuardRocketMqProperties;
import io.github.actionguard.adapter.rocketmq.consumer.RocketMqActionExecutionConsumer;
import io.github.actionguard.adapter.rocketmq.consumer.RocketMqConsumerTestBridge;
import io.github.actionguard.adapter.rocketmq.producer.RocketMqActionExecutionMessageProducer;
import io.github.actionguard.adapter.rocketmq.support.RocketMqConsumeStrategy;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.repository.ActionConsumeLogRepository;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.runtime.execution.ActionExecutionCallback;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageFactory;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import io.github.actionguard.demo.ActionGuardDemoApplication;
import io.github.actionguard.starter.scheduler.ActionOutboxRecoveryScheduler;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证发送异常后的自动补发。这里用测试 SDK 客户端模拟 send 异常，
 * 不连接 RocketMQ，也不代表 Broker 断连或网络恢复验证。
 */
@SpringBootTest(classes = {ActionGuardDemoApplication.class, DemoRocketMqRecoveryTest.TestConfiguration.class}, properties = {
        "spring.config.import=optional:classpath:/missing-action-guard-test-config.properties",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:rocketmq_recovery_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false",
        "demo.runner.enabled=false",
        "action.guard.store.type=mysql",
        "action.guard.execution.transport=rocketmq",
        "action.guard.recovery.enabled=true",
        "action.guard.recovery.fixed-delay=1s",
        "action.guard.recovery.batch-size=10",
        "action.guard.rocketmq.name-server=test-name-server:9876",
        "action.guard.rocketmq.topic=action-guard-recovery-test",
        "action.guard.rocketmq.producer-group=action-guard-recovery-producer",
        "action.guard.rocketmq.consumer-group=action-guard-recovery-consumer",
        "action.guard.rocketmq.consumer-enabled=false",
        "action.guard.rocketmq.startup-probe-enabled=false"
})
@AutoConfigureMockMvc
class DemoRocketMqRecoveryTest {

    @Autowired private MockMvc mvc;
    @Autowired private ActionOutboxRepository outboxes;
    @Autowired private ActionInstanceRepository actions;
    @Autowired private ActionExecutionCallback callback;
    @Autowired private ActionConsumeLogRepository consumeLogs;
    @Autowired private ActionObservabilityService observability;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ActionGuardRocketMqProperties properties;
    @Autowired private ToggleableProducerClient client;
    @Autowired private ActionOutboxRecoveryScheduler scheduler;

    @AfterEach
    void stopScheduler() {
        scheduler.stop();
    }

    @Test
    void shouldAutomaticallyRecoverFailedRocketMqDeliveryThenExecuteOnlyQueuedMessage() throws Exception {
        String actionId = publish();
        ActionOutbox failed = awaitOutbox(actionId, ActionOutboxStatus.NEW, Duration.ofSeconds(3));

        assertThat(failed.deliveryAttemptCount()).isEqualTo(1);
        assertThat(client.messages).isEmpty();
        assertThat(actions.findById(actionId).orElseThrow().status().name()).isNotEqualTo("SUCCESS");
        String dispatchId = failed.dispatchId();

        client.failSends.set(false);
        ActionOutbox done = awaitOutbox(actionId, ActionOutboxStatus.DONE, Duration.ofSeconds(10));
        assertThat(done.dispatchId()).isEqualTo(dispatchId);
        assertThat(done.deliveryAttemptCount()).isEqualTo(1);
        assertThat(client.messages).hasSize(1);
        assertThat(actions.findById(actionId).orElseThrow().status().name()).isNotEqualTo("SUCCESS");

        RocketMqActionExecutionConsumer consumer = new RocketMqActionExecutionConsumer(
                objectMapper, consumeLogs, callback, properties, Clock.systemUTC(),
                new RocketMqConsumeStrategy(properties.getMaxRedeliveries()), observability);
        MessageExt message = toMessageExt(client.messages.removeFirst());
        assertThat(RocketMqConsumerTestBridge.consume(consumer, message))
                .isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        mvc.perform(get("/api/actions/{id}", actionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        assertThat(RocketMqConsumerTestBridge.consume(consumer, message))
                .isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        assertThat(actions.findById(actionId).orElseThrow().status().name()).isEqualTo("SUCCESS");
    }

    private String publish() throws Exception {
        String bizKey = "rocketmq-recovery:" + UUID.randomUUID();
        String response = mvc.perform(post("/api/publish")
                        .contentType("application/json")
                        .content("""
                                {"actionName":"demo-notify-success","bizKey":"%s","phoneNumber":"13800000000"}
                                """.formatted(bizKey)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("actionInstanceId").asText();
    }

    private ActionOutbox awaitOutbox(String actionId, ActionOutboxStatus expected, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        ActionOutbox current = null;
        while (Instant.now().isBefore(deadline)) {
            current = outboxes.findByActionInstanceId(actionId).orElseThrow();
            if (current.status() == expected) {
                return current;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Outbox did not reach " + expected + "; last=" + current);
    }

    private MessageExt toMessageExt(Message source) {
        MessageExt message = new MessageExt();
        message.setTopic(source.getTopic());
        message.setBody(source.getBody());
        message.setKeys(source.getKeys());
        message.putUserProperty("actionGuardMessageKey", source.getUserProperty("actionGuardMessageKey"));
        message.putUserProperty("actionGuardOutboxId", source.getUserProperty("actionGuardOutboxId"));
        message.putUserProperty("actionGuardActionInstanceId", source.getUserProperty("actionGuardActionInstanceId"));
        return message;
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class TestConfiguration {

        @Bean
        ToggleableProducerClient toggleableProducerClient() {
            return new ToggleableProducerClient();
        }

        @Bean
        @Primary
        ActionExecutionMessageProducer actionExecutionMessageProducer(
                ToggleableProducerClient client,
                ObjectMapper objectMapper,
                ActionGuardRocketMqProperties properties) {
            return new RocketMqActionExecutionMessageProducer(
                    client, objectMapper, new ActionExecutionMessageFactory(), properties);
        }
    }

    static final class ToggleableProducerClient extends DefaultMQProducer {
        final AtomicBoolean failSends = new AtomicBoolean(true);
        final Deque<Message> messages = new ArrayDeque<>();

        @Override
        public void start() {
        }

        @Override
        public java.util.List<MessageQueue> fetchPublishMessageQueues(String topic) {
            return Collections.singletonList(new MessageQueue(topic, "test-broker", 0));
        }

        @Override
        public SendResult send(Message message, long timeout) {
            if (failSends.get()) {
                throw new IllegalStateException("simulated RocketMQ send failure");
            }
            messages.addLast(message);
            return new SendResult(SendStatus.SEND_OK, "test-message-id", "test-offset-id", null, 0L);
        }
    }
}
