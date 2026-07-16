package io.github.actionguard.adapter.rabbitmq.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.actionguard.adapter.rabbitmq.config.ActionGuardRabbitMqProperties;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageFactory;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.core.RabbitOperations.OperationsCallback;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMqActionExecutionMessageProducerTest {

    @Test
    void shouldPublishPersistentJsonMessageWithStableIdentity() throws Exception {
        CapturingRabbitTemplate rabbitTemplate = new CapturingRabbitTemplate();
        ActionGuardRabbitMqProperties properties = new ActionGuardRabbitMqProperties();
        properties.setExchange("action.guard.execute");
        properties.setRoutingKeyPrefix("action.execute");
        RabbitMqActionExecutionMessageProducer producer = new RabbitMqActionExecutionMessageProducer(
                rabbitTemplate,
                new ObjectMapper().findAndRegisterModules(),
                new ActionExecutionMessageFactory(),
                properties
        );

        producer.publish(new ActionOutbox(
                "outbox-1",
                "action-1",
                "ACTION_EXECUTE",
                ActionOutboxStatus.NEW,
                Instant.parse("2026-06-26T08:10:00Z"),
                0,
                0,
                Instant.parse("2026-06-26T08:10:00Z"),
                Instant.parse("2026-06-26T08:10:00Z")
        ));

        assertThat(rabbitTemplate.exchange).isEqualTo("action.guard.execute");
        assertThat(rabbitTemplate.routingKey).isEqualTo("action.execute.action-1");
        assertThat(rabbitTemplate.message.getMessageProperties().getMessageId()).isEqualTo("ACTION_EXECUTE:outbox-1");
        assertThat(rabbitTemplate.message.getMessageProperties().getHeaders())
                .containsEntry("x-action-guard-message-key", "ACTION_EXECUTE:action-1")
                .containsEntry("x-action-guard-outbox-id", "outbox-1");
        String payload = new String(rabbitTemplate.message.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(payload).contains("\"messageId\":\"ACTION_EXECUTE:outbox-1\"");
        assertThat(payload).contains("\"actionInstanceId\":\"action-1\"");
    }

    private static final class CapturingRabbitTemplate extends RabbitTemplate {
        private String exchange;
        private String routingKey;
        private Message message;

        @Override
        public void send(String exchange, String routingKey, Message message) {
            this.exchange = exchange;
            this.routingKey = routingKey;
            this.message = message;
        }

        @Override
        public <T> T invoke(OperationsCallback<T> action) {
            return action.doInRabbit(this);
        }

        @Override
        public boolean waitForConfirms(long timeout) {
            return true;
        }
    }
}
