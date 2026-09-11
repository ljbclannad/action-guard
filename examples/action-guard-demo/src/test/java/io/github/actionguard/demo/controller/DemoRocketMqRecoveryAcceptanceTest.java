package io.github.actionguard.demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.actionguard.adapter.rocketmq.config.ActionGuardRocketMqProperties;
import io.github.actionguard.adapter.rocketmq.producer.RocketMqActionExecutionMessageProducer;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageFactory;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import io.github.actionguard.demo.ActionGuardDemoApplication;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 使用真实 RocketMQ 收发验证发送失败注入后的自动恢复。
 *
 * <p>默认跳过；只有显式传入 {@code -Daction.guard.acceptance.rocketmq.enabled=true} 才会创建
 * RocketMQ 客户端。该测试模拟的是测试进程内发送失败，不代表网络断连或 SDK 重连验证。</p>
 */
@EnabledIfSystemProperty(named = "action.guard.acceptance.rocketmq.enabled", matches = "true")
@SpringBootTest(classes = {
        ActionGuardDemoApplication.class,
        DemoRocketMqRecoveryAcceptanceTest.AcceptanceConfiguration.class
}, properties = {
        "spring.config.import=optional:classpath:/missing-action-guard-test-config.properties",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:rocketmq_recovery_acceptance;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
        "action.guard.rocketmq.name-server=${action.guard.acceptance.rocketmq.name-server:}",
        "action.guard.rocketmq.topic=${action.guard.acceptance.rocketmq.topic:}",
        "action.guard.rocketmq.producer-group=${action.guard.acceptance.rocketmq.producer-group:}",
        "action.guard.rocketmq.consumer-group=${action.guard.acceptance.rocketmq.consumer-group:}",
        "action.guard.rocketmq.startup-probe-enabled=false",
        "action.guard.rocketmq.consumer-enabled=true"
})
@AutoConfigureMockMvc
class DemoRocketMqRecoveryAcceptanceTest {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mvc;

    @org.springframework.beans.factory.annotation.Autowired
    private ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private ActionOutboxRepository outboxes;

    @org.springframework.beans.factory.annotation.Autowired
    private ActionInstanceRepository actions;

    @org.springframework.beans.factory.annotation.Autowired
    private FailingOnceProducer failingOnceProducer;

    @Test
    void shouldRecoverInjectedSendFailureThroughRealRocketMqConsumer() throws Exception {
        String actionId = publish();
        ActionOutbox failed = awaitOutbox(actionId, ActionOutboxStatus.NEW, Duration.ofSeconds(10));
        String dispatchId = failed.dispatchId();

        assertThat(failed.deliveryAttemptCount()).isEqualTo(1);
        assertThat(actions.findById(actionId).orElseThrow().status().name()).isNotEqualTo("SUCCESS");

        failingOnceProducer.allowSends();
        ActionOutbox done = awaitOutbox(actionId, ActionOutboxStatus.DONE, Duration.ofSeconds(20));
        assertThat(done.dispatchId()).isEqualTo(dispatchId);
        assertThat(done.deliveryAttemptCount()).isEqualTo(1);

        awaitActionSuccess(actionId, Duration.ofSeconds(30));
    }

    private String publish() throws Exception {
        String bizKey = "rocketmq-recovery-acceptance:" + UUID.randomUUID();
        String response = mvc.perform(post("/api/publish")
                        .contentType("application/json")
                        .content("""
                                {"actionName":"demo-notify-success","bizKey":"%s","phoneNumber":"13800000000"}
                                """.formatted(bizKey)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("actionInstanceId").asText();
    }

    private ActionOutbox awaitOutbox(String actionId, ActionOutboxStatus expected, Duration timeout)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        ActionOutbox current = null;
        while (Instant.now().isBefore(deadline)) {
            current = outboxes.findByActionInstanceId(actionId).orElseThrow();
            if (current.status() == expected) {
                return current;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Outbox did not reach " + expected + "; actionId=" + actionId + "; last=" + current);
    }

    private void awaitActionSuccess(String actionId, Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if ("SUCCESS".equals(actions.findById(actionId).orElseThrow().status().name())) {
                mvc.perform(get("/api/actions/{id}", actionId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("SUCCESS"));
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Action did not reach SUCCESS; actionId=" + actionId
                + "; outbox=" + outboxes.findByActionInstanceId(actionId).orElse(null));
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class AcceptanceConfiguration {

        @Bean
        static BeanFactoryPostProcessor requiredRocketMqAcceptanceProperties() {
            return beanFactory -> {
                Environment environment = beanFactory.getBean(Environment.class);
                String missing = Stream.of(
                                "action.guard.acceptance.rocketmq.name-server",
                                "action.guard.acceptance.rocketmq.topic",
                                "action.guard.acceptance.rocketmq.producer-group",
                                "action.guard.acceptance.rocketmq.consumer-group")
                        .filter(key -> {
                            String value = environment.getProperty(key);
                            return value == null || value.isBlank();
                        })
                        .collect(Collectors.joining(", "));
                if (!missing.isBlank()) {
                    throw new BeansException("真实 RocketMQ 验收缺少必填参数: " + missing) {
                    };
                }
            };
        }

        @Bean(destroyMethod = "shutdown")
        DefaultMQProducer acceptanceRocketMqProducer(ActionGuardRocketMqProperties properties) {
            DefaultMQProducer producer = new DefaultMQProducer(properties.getProducerGroup());
            producer.setNamesrvAddr(properties.getNameServer());
            return producer;
        }

        @Bean
        FailingOnceProducer failingOnceProducer(
                DefaultMQProducer acceptanceRocketMqProducer,
                ObjectMapper objectMapper,
                ActionGuardRocketMqProperties properties) {
            ActionExecutionMessageProducer delegate = new RocketMqActionExecutionMessageProducer(
                    acceptanceRocketMqProducer, objectMapper, new ActionExecutionMessageFactory(), properties);
            return new FailingOnceProducer(delegate);
        }

        @Bean
        @Primary
        ActionExecutionMessageProducer actionExecutionMessageProducer(FailingOnceProducer failingOnceProducer) {
            return failingOnceProducer;
        }
    }

    static final class FailingOnceProducer implements ActionExecutionMessageProducer {
        private final ActionExecutionMessageProducer delegate;
        private final AtomicBoolean failSends = new AtomicBoolean(true);

        FailingOnceProducer(ActionExecutionMessageProducer delegate) {
            this.delegate = delegate;
        }

        @Override
        public void publish(ActionOutbox outbox) {
            if (failSends.get()) {
                throw new IllegalStateException("simulated RocketMQ send failure for acceptance test");
            }
            delegate.publish(outbox);
        }

        void allowSends() {
            failSends.set(false);
        }
    }
}
