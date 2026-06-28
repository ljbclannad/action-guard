package io.github.actionguard.core.runtime;

import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.repository.InMemoryActionOutboxRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ActionOutboxRecoveryServiceTest {

    @Test
    void shouldRecoverDueNewOutboxAndMarkDone() {
        InMemoryActionOutboxRepository repository = new InMemoryActionOutboxRepository();
        CapturingProducer producer = new CapturingProducer();
        Clock clock = Clock.fixed(Instant.parse("2026-06-27T08:00:00Z"), ZoneOffset.UTC);
        repository.save(new ActionOutbox(
                "outbox-1", "act-1", "ACTION_EXECUTE", ActionOutboxStatus.NEW,
                Instant.parse("2026-06-27T07:59:00Z"), 1, 0,
                Instant.parse("2026-06-27T07:58:00Z"), Instant.parse("2026-06-27T07:58:00Z")
        ));
        ActionOutboxRecoveryService service = new ActionOutboxRecoveryService(
                repository,
                Optional.of(producer),
                new ActionObservabilityService(Optional.empty(), Optional.empty(), clock),
                clock
        );

        int recovered = service.recoverDueOutboxes(10, Duration.ofSeconds(30));

        assertThat(recovered).isEqualTo(1);
        assertThat(producer.published).hasSize(1);
        assertThat(repository.findById("outbox-1").orElseThrow().status()).isEqualTo(ActionOutboxStatus.DONE);
    }

    @Test
    void shouldRecoverStaleClaimedOutbox() {
        InMemoryActionOutboxRepository repository = new InMemoryActionOutboxRepository();
        CapturingProducer producer = new CapturingProducer();
        Clock clock = Clock.fixed(Instant.parse("2026-06-27T08:00:00Z"), ZoneOffset.UTC);
        repository.save(new ActionOutbox(
                "outbox-2", "act-2", "ACTION_EXECUTE", ActionOutboxStatus.CLAIMED,
                Instant.parse("2026-06-27T07:59:00Z"), 0, 0,
                Instant.parse("2026-06-27T07:50:00Z"), Instant.parse("2026-06-27T07:50:00Z")
        ));
        ActionOutboxRecoveryService service = new ActionOutboxRecoveryService(
                repository,
                Optional.of(producer),
                new ActionObservabilityService(Optional.empty(), Optional.empty(), clock),
                clock
        );

        int recovered = service.recoverDueOutboxes(10, Duration.ofSeconds(30));

        assertThat(recovered).isEqualTo(1);
        assertThat(repository.findById("outbox-2").orElseThrow().status()).isEqualTo(ActionOutboxStatus.DONE);
    }

    @Test
    void shouldResetOutboxToNewWhenRecoveryPublishFails() {
        InMemoryActionOutboxRepository repository = new InMemoryActionOutboxRepository();
        FailingProducer producer = new FailingProducer();
        Clock clock = Clock.fixed(Instant.parse("2026-06-27T08:00:00Z"), ZoneOffset.UTC);
        repository.save(new ActionOutbox(
                "outbox-3", "act-3", "ACTION_EXECUTE", ActionOutboxStatus.NEW,
                Instant.parse("2026-06-27T07:59:00Z"), 2, 0,
                Instant.parse("2026-06-27T07:58:00Z"), Instant.parse("2026-06-27T07:58:00Z")
        ));
        ActionOutboxRecoveryService service = new ActionOutboxRecoveryService(
                repository,
                Optional.of(producer),
                new ActionObservabilityService(Optional.empty(), Optional.empty(), clock),
                clock
        );

        int recovered = service.recoverDueOutboxes(10, Duration.ofSeconds(30));

        assertThat(recovered).isZero();
        assertThat(repository.findById("outbox-3").orElseThrow().status()).isEqualTo(ActionOutboxStatus.NEW);
    }

    @Test
    void shouldAllowOnlyOneNodeToRecoverSameOutboxInSingleRound() throws Exception {
        CoordinatedOutboxRepository repository = new CoordinatedOutboxRepository(2);
        CapturingProducer producer = new CapturingProducer();
        Clock clock = Clock.fixed(Instant.parse("2026-06-27T08:00:00Z"), ZoneOffset.UTC);
        repository.save(new ActionOutbox(
                "outbox-4", "act-4", "ACTION_EXECUTE", ActionOutboxStatus.NEW,
                Instant.parse("2026-06-27T07:59:00Z"), 0, 0,
                Instant.parse("2026-06-27T07:58:00Z"), Instant.parse("2026-06-27T07:58:00Z")
        ));
        ActionOutboxRecoveryService nodeA = new ActionOutboxRecoveryService(
                repository,
                Optional.of(producer),
                new ActionObservabilityService(Optional.empty(), Optional.empty(), clock),
                clock
        );
        ActionOutboxRecoveryService nodeB = new ActionOutboxRecoveryService(
                repository,
                Optional.of(producer),
                new ActionObservabilityService(Optional.empty(), Optional.empty(), clock),
                clock
        );

        List<Integer> recoveredCounts = new CopyOnWriteArrayList<>();
        Thread threadA = new Thread(() -> recoveredCounts.add(nodeA.recoverDueOutboxes(10, Duration.ofSeconds(30))));
        Thread threadB = new Thread(() -> recoveredCounts.add(nodeB.recoverDueOutboxes(10, Duration.ofSeconds(30))));

        threadA.start();
        threadB.start();
        threadA.join(2000);
        threadB.join(2000);

        assertThat(recoveredCounts).hasSize(2);
        assertThat(recoveredCounts.stream().mapToInt(Integer::intValue).sum()).isEqualTo(1);
        assertThat(producer.published).hasSize(1);
        assertThat(repository.findById("outbox-4").orElseThrow().status()).isEqualTo(ActionOutboxStatus.DONE);
    }

    private static final class CapturingProducer implements ActionExecutionMessageProducer {
        private final List<ActionOutbox> published = new ArrayList<>();

        @Override
        public void publish(ActionOutbox outbox) {
            published.add(outbox);
        }
    }

    private static final class FailingProducer implements ActionExecutionMessageProducer {
        @Override
        public void publish(ActionOutbox outbox) {
            throw new IllegalStateException("simulated recovery publish failure");
        }
    }

    private static final class CoordinatedOutboxRepository extends InMemoryActionOutboxRepository {
        private final CountDownLatch readersReady;
        private final CountDownLatch releaseReaders = new CountDownLatch(1);

        private CoordinatedOutboxRepository(int concurrentReaders) {
            this.readersReady = new CountDownLatch(concurrentReaders);
        }

        @Override
        public List<ActionOutbox> findRecoverable(Instant availableBeforeOrAt, Instant claimedBeforeOrAt, int limit) {
            List<ActionOutbox> recoverable = super.findRecoverable(availableBeforeOrAt, claimedBeforeOrAt, limit);
            if (!recoverable.isEmpty()) {
                readersReady.countDown();
                try {
                    if (readersReady.await(2, TimeUnit.SECONDS)) {
                        releaseReaders.countDown();
                    }
                    if (!releaseReaders.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out coordinating recoverable reads");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while coordinating recoverable reads", ex);
                }
            }
            return recoverable;
        }
    }
}
