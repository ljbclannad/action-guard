package io.github.actionguard.core.repository;

import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.model.ActionStepStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryFencingRepositoriesTest {

    @Test
    void shouldRejectStaleActionInstanceUpdate() {
        InMemoryActionInstanceRepository repository = new InMemoryActionInstanceRepository();
        Instant now = Instant.parse("2026-06-26T12:00:00Z");
        repository.save(new ActionInstance("act-1", "order-cancel-flow", "order:1", ActionStatus.NEW, 0, 1, Map.of(), null, null, 0, now, now));

        repository.save(new ActionInstance("act-1", "order-cancel-flow", "order:1", ActionStatus.DISPATCHING, 0, 1, Map.of(), null, null, 0, now, now));

        assertThatThrownBy(() -> repository.save(new ActionInstance("act-1", "order-cancel-flow", "order:1", ActionStatus.SUCCESS, 1, 1, Map.of(), null, null, 0, now, now)))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void shouldRejectStaleActionStepInstanceUpdate() {
        InMemoryActionStepInstanceRepository repository = new InMemoryActionStepInstanceRepository();
        Instant now = Instant.parse("2026-06-26T12:00:00Z");
        repository.save(new ActionStepInstance("step-1", "act-1", 0, "send-user-sms", "SMS", "notify.user", ActionStepStatus.PENDING, 0, Map.of(), null, null, 0, now, now));

        repository.save(new ActionStepInstance("step-1", "act-1", 0, "send-user-sms", "SMS", "notify.user", ActionStepStatus.SUCCESS, 1, Map.of(), null, null, 0, now, now));

        assertThatThrownBy(() -> repository.save(new ActionStepInstance("step-1", "act-1", 0, "send-user-sms", "SMS", "notify.user", ActionStepStatus.FAILED, 1, Map.of(), "ERR", "boom", 0, now, now)))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void shouldRejectStaleActionOutboxUpdate() {
        InMemoryActionOutboxRepository repository = new InMemoryActionOutboxRepository();
        Instant now = Instant.parse("2026-06-26T12:00:00Z");
        repository.save(new ActionOutbox("outbox-1", "act-1", "ACTION_EXECUTE", ActionOutboxStatus.NEW, now, 0, 0, now, now));

        repository.save(new ActionOutbox("outbox-1", "act-1", "ACTION_EXECUTE", ActionOutboxStatus.DONE, now, 0, 0, now, now));

        assertThatThrownBy(() -> repository.save(new ActionOutbox("outbox-1", "act-1", "ACTION_EXECUTE", ActionOutboxStatus.DEAD, now, 1, 0, now, now)))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
