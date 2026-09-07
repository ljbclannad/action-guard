package io.github.actionguard.core.runtime.execution;

import io.github.actionguard.api.runtime.ActionAlertEvent;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.repository.InMemoryActionOutboxRepository;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionOutboxDispatcherTest {

    private final Instant now = Instant.parse("2026-09-07T00:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final InMemoryActionOutboxRepository repository = new InMemoryActionOutboxRepository();
    private final List<ActionAlertEvent> alerts = new ArrayList<>();
    private final ActionObservabilityService observability = new ActionObservabilityService(
            Optional.of(alerts::add), Optional.empty(), clock);

    @Test
    void shouldClaimBeforeSendingAndRejectStaleCandidate() {
        ActionOutbox candidate = candidate(now);
        AtomicInteger sent = new AtomicInteger();
        ActionOutboxDispatcher dispatcher = dispatcher(outbox -> {
            assertThat(repository.findById(outbox.id()).orElseThrow().status()).isEqualTo(ActionOutboxStatus.CLAIMED);
            assertThat(outbox.dispatchId()).isEqualTo(candidate.dispatchId());
            sent.incrementAndGet();
        });

        assertThat(dispatcher.dispatch(candidate, 1)).isTrue();
        assertThat(dispatcher.dispatch(candidate, 1)).isFalse();
        assertThat(sent.get()).isEqualTo(1);
        assertThat(repository.findById(candidate.id()).orElseThrow().status()).isEqualTo(ActionOutboxStatus.DONE);
    }

    @Test
    void shouldRetryUsingUpdatedVersionAndPreserveDispatchId() {
        ActionOutbox candidate = candidate(now);
        AtomicInteger sent = new AtomicInteger();
        ActionOutboxDispatcher dispatcher = dispatcher(outbox -> {
            assertThat(outbox.dispatchId()).isEqualTo(candidate.dispatchId());
            if (sent.incrementAndGet() == 1) {
                throw new IllegalStateException("发送失败");
            }
        });

        assertThat(dispatcher.dispatch(candidate, 2)).isTrue();
        ActionOutbox persisted = repository.findById(candidate.id()).orElseThrow();
        assertThat(persisted.status()).isEqualTo(ActionOutboxStatus.DONE);
        assertThat(persisted.attemptCount()).isEqualTo(3);
        assertThat(sent.get()).isEqualTo(2);
        assertThat(alerts).isEmpty();
    }

    @Test
    void shouldResetAndAlertOnceAfterAttemptsExhausted() {
        ActionOutbox candidate = candidate(now);
        AtomicInteger sent = new AtomicInteger();
        ActionOutboxDispatcher dispatcher = dispatcher(outbox -> {
            sent.incrementAndGet();
            throw new IllegalStateException("发送失败");
        });

        assertThat(dispatcher.dispatch(candidate, 2)).isFalse();
        ActionOutbox persisted = repository.findById(candidate.id()).orElseThrow();
        assertThat(persisted.status()).isEqualTo(ActionOutboxStatus.NEW);
        assertThat(persisted.attemptCount()).isEqualTo(4);
        assertThat(sent.get()).isEqualTo(2);
        assertThat(alerts).hasSize(1);
    }

    @Test
    void shouldLeaveFutureAndProducerlessOutboxesUntouched() {
        ActionOutbox future = candidate(now.plusSeconds(60));
        AtomicInteger sent = new AtomicInteger();
        assertThat(dispatcher(outbox -> sent.incrementAndGet()).dispatch(future, 1)).isFalse();
        assertThat(repository.findById(future.id()).orElseThrow()).isEqualTo(future);
        ActionOutbox due = candidate(now);
        assertThat(new ActionOutboxDispatcher(repository, Optional.empty(), observability, clock)
                .dispatch(due, 1)).isFalse();
        assertThat(repository.findById(due.id()).orElseThrow()).isEqualTo(due);
        assertThat(sent.get()).isZero();
    }

    @Test
    void shouldNotRedispatchTerminalOutboxes() {
        AtomicInteger sent = new AtomicInteger();
        ActionOutboxDispatcher dispatcher = dispatcher(outbox -> sent.incrementAndGet());
        for (ActionOutboxStatus status : List.of(ActionOutboxStatus.DONE, ActionOutboxStatus.DEAD)) {
            ActionOutbox terminal = repository.save(new ActionOutbox(status.name(), "action", "ACTION_EXECUTE",
                    status, now, 0, 0, now, now));
            assertThat(dispatcher.dispatch(terminal, 1)).isFalse();
            assertThat(repository.findById(terminal.id()).orElseThrow()).isEqualTo(terminal);
        }
        assertThat(sent.get()).isZero();
    }

    @Test
    void shouldNotResetNewerDispatchAfterSuccessfulSend() {
        assertNewerDispatchPreserved(false);
    }

    @Test
    void shouldNotResetNewerDispatchAfterFailedSend() {
        assertNewerDispatchPreserved(true);
    }

    private void assertNewerDispatchPreserved(boolean failSend) {
        ActionOutbox candidate = candidate(now);
        AtomicInteger sent = new AtomicInteger();
        ActionOutboxDispatcher dispatcher = dispatcher(outbox -> {
            sent.incrementAndGet();
            repository.save(new ActionOutbox(outbox.id(), outbox.actionInstanceId(), outbox.topic(),
                    "next-dispatch", ActionOutboxStatus.NEW, now, outbox.attemptCount(),
                    outbox.version(), now, now));
            if (failSend) {
                throw new IllegalStateException("发送结果未知");
            }
        });

        assertThat(dispatcher.dispatch(candidate, 3)).isFalse();
        ActionOutbox persisted = repository.findById(candidate.id()).orElseThrow();
        assertThat(persisted.dispatchId()).isEqualTo("next-dispatch");
        assertThat(persisted.status()).isEqualTo(ActionOutboxStatus.NEW);
        assertThat(sent.get()).isEqualTo(1);
    }

    @Test
    void shouldLeaveClaimRecoverableWhenCompletionPersistenceFails() {
        InMemoryActionOutboxRepository failingRepository = new InMemoryActionOutboxRepository() {
            @Override
            public ActionOutbox save(ActionOutbox outbox) {
                if (outbox.status() == ActionOutboxStatus.DONE) {
                    throw new IllegalStateException("完成状态落库失败");
                }
                return super.save(outbox);
            }
        };
        ActionOutbox candidate = failingRepository.save(candidate(now));
        AtomicInteger sent = new AtomicInteger();
        ActionOutboxDispatcher dispatcher = new ActionOutboxDispatcher(failingRepository,
                Optional.of(outbox -> sent.incrementAndGet()), observability, clock);

        assertThatThrownBy(() -> dispatcher.dispatch(candidate, 3))
                .isInstanceOf(IllegalStateException.class).hasMessage("完成状态落库失败");
        assertThat(sent.get()).isEqualTo(1);
        assertThat(failingRepository.findById(candidate.id()).orElseThrow().status()).isEqualTo(ActionOutboxStatus.CLAIMED);
    }

    private ActionOutbox candidate(Instant availableAt) {
        return repository.save(new ActionOutbox("outbox", "action", "ACTION_EXECUTE", "dispatch",
                ActionOutboxStatus.NEW, availableAt, 2, 0, now, now));
    }

    private ActionOutboxDispatcher dispatcher(ActionExecutionMessageProducer producer) {
        return new ActionOutboxDispatcher(repository, Optional.of(producer), observability, clock);
    }
}
