package io.github.actionguard.adapter.rocketmq.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.actionguard.adapter.rocketmq.config.ActionGuardRocketMqProperties;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageFactory;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RocketMqActionExecutionMessageProducerTest {

    @Test
    void shouldPublishJsonMessageWithStableIdentity() {
        CapturingProducer client = new CapturingProducer();
        ActionGuardRocketMqProperties properties = new ActionGuardRocketMqProperties();
        properties.setTopic("action-guard-execute");
        RocketMqActionExecutionMessageProducer producer = new RocketMqActionExecutionMessageProducer(
                client, new ObjectMapper().findAndRegisterModules(), new ActionExecutionMessageFactory(), properties);

        producer.publish(new ActionOutbox(
                "outbox-1", "action-1", "ACTION_EXECUTE", ActionOutboxStatus.NEW,
                Instant.parse("2026-06-26T08:10:00Z"), 0, 0,
                Instant.parse("2026-06-26T08:10:00Z"), Instant.parse("2026-06-26T08:10:00Z")));

        assertThat(client.message.getTopic()).isEqualTo("action-guard-execute");
        assertThat(client.message.getKeys()).isEqualTo("ACTION_EXECUTE:outbox-1");
        assertThat(client.message.getUserProperty("actionGuardMessageKey")).isEqualTo("ACTION_EXECUTE:action-1");
        assertThat(client.message.getUserProperty("actionGuardOutboxId")).isEqualTo("outbox-1");
        assertThat(new String(client.message.getBody())).contains("\"messageId\":\"ACTION_EXECUTE:outbox-1\"");
    }

    private static final class CapturingProducer extends DefaultMQProducer {
        private Message message;

        @Override
        public void start() {
        }

        @Override
        public SendResult send(Message message, long timeout) {
            this.message = message;
            return new SendResult(SendStatus.SEND_OK, "msg-id", "offset-id", null, 0L);
        }
    }
}
