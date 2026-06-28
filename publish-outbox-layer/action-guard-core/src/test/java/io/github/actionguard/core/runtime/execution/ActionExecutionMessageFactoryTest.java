package io.github.actionguard.core.runtime.execution;

import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ActionExecutionMessageFactoryTest {

    private final ActionExecutionMessageFactory factory = new ActionExecutionMessageFactory();

    @Test
    void shouldCreateStableMessageIdentityFromOutbox() {
        ActionOutbox outbox = new ActionOutbox(
                "outbox-1",
                "action-1",
                "ACTION_EXECUTE",
                ActionOutboxStatus.NEW,
                Instant.parse("2026-06-26T08:00:00Z"),
                0,
                0,
                Instant.parse("2026-06-26T08:00:00Z"),
                Instant.parse("2026-06-26T08:00:00Z")
        );

        ActionExecutionMessage message = factory.create(outbox);

        assertThat(message.messageId()).isEqualTo("ACTION_EXECUTE:outbox-1");
        assertThat(message.messageKey()).isEqualTo("ACTION_EXECUTE:action-1");
        assertThat(message.outboxId()).isEqualTo("outbox-1");
        assertThat(message.actionInstanceId()).isEqualTo("action-1");
    }
}
