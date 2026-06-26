package io.github.actionguard.core.repository;

import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.core.model.ActionConsumeStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryActionConsumeLogRepositoryTest {

    @Test
    void shouldFenceDuplicateConsumptionByMessageId() {
        InMemoryActionConsumeLogRepository repository = new InMemoryActionConsumeLogRepository();
        ActionExecutionMessage message = new ActionExecutionMessage(
                "ACTION_EXECUTE:outbox-1",
                "ACTION_EXECUTE:action-1",
                "outbox-1",
                "action-1",
                "ACTION_EXECUTE",
                Instant.parse("2026-06-26T08:40:00Z")
        );
        Instant now = Instant.parse("2026-06-26T08:41:00Z");

        assertThat(repository.tryStartConsumption(message, "rabbitmq-main", now)).isTrue();
        assertThat(repository.tryStartConsumption(message, "rabbitmq-main", now)).isFalse();

        repository.markDuplicateSkipped(message.messageId(), "rabbitmq-main", now.plusSeconds(1));

        assertThat(repository.findByMessageId(message.messageId())).isPresent();
        assertThat(repository.findByMessageId(message.messageId()).orElseThrow().consumeStatus())
                .isEqualTo(ActionConsumeStatus.DUPLICATE_SKIPPED);
        assertThat(repository.findByMessageId(message.messageId()).orElseThrow().attemptCount()).isEqualTo(2);
    }
}
