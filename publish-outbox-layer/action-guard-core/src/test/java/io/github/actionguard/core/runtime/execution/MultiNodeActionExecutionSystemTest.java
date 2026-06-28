package io.github.actionguard.core.runtime.execution;

import io.github.actionguard.core.runtime.definition.InMemoryActionDefinitionRegistry;
import io.github.actionguard.core.runtime.definition.ActionDefinitionRegistry;
import io.github.actionguard.core.runtime.definition.ActionDefinitionValidator;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import io.github.actionguard.core.runtime.publish.DefaultActionPublisher;
import io.github.actionguard.core.runtime.recovery.ActionOutboxRecoveryService;
import io.github.actionguard.core.runtime.registry.StepHandlerRegistry;
import io.github.actionguard.core.runtime.retry.FixedAttemptActionRetryPolicy;
import io.github.actionguard.api.ActionRequest;
import io.github.actionguard.api.definition.ActionDefinition;
import io.github.actionguard.api.definition.ActionStepDefinition;
import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.api.runtime.ActionStepContext;
import io.github.actionguard.api.runtime.StepExecutionResult;
import io.github.actionguard.api.spi.ActionStepHandler;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.model.ActionStepStatus;
import io.github.actionguard.core.repository.InMemoryActionInstanceRepository;
import io.github.actionguard.core.repository.InMemoryActionOutboxRepository;
import io.github.actionguard.core.repository.InMemoryActionStepInstanceRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MultiNodeActionExecutionSystemTest {

    @Test
    void shouldProgressActionAcrossTwoNodesWithSingleRecoveryOwnership() throws Exception {
        Instant now = Instant.parse("2026-06-26T10:00:00Z");
        ActionDefinitionRegistry definitionRegistry = new InMemoryActionDefinitionRegistry(
                List.of(new ActionDefinition(
                        "order-cancel-flow",
                        "demo",
                        false,
                        List.of(
                                new ActionStepDefinition("send-cancel-event", "MQ_MESSAGE", "order.cancel.exchange", null, null, null),
                                new ActionStepDefinition("send-user-sms", "SMS", "notify.user", null, null, null)
                        )
                )),
                new ActionDefinitionValidator()
        );
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        CoordinatedActionOutboxRepository actionOutboxRepository = new CoordinatedActionOutboxRepository(2);
        QueueingMessageProducer producer = new QueueingMessageProducer();
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);

        new DefaultActionPublisher(
                definitionRegistry,
                actionInstanceRepository,
                actionStepInstanceRepository,
                actionOutboxRepository,
                clock
        ).publish(new ActionRequest("order-cancel-flow", "order:1", Map.of("operator", "demo"), List.of()));

        ActionOutboxRecoveryService recoveryNodeA = new ActionOutboxRecoveryService(
                actionOutboxRepository,
                Optional.of(producer),
                new ActionObservabilityService(Optional.empty(), Optional.empty(), clock),
                clock
        );
        ActionOutboxRecoveryService recoveryNodeB = new ActionOutboxRecoveryService(
                actionOutboxRepository,
                Optional.of(producer),
                new ActionObservabilityService(Optional.empty(), Optional.empty(), clock),
                clock
        );
        List<Integer> recoveredCounts = new CopyOnWriteArrayList<>();
        List<Throwable> recoveryFailures = new CopyOnWriteArrayList<>();
        Thread nodeA = new Thread(() -> recoverOnNode(recoveryNodeA, recoveredCounts, recoveryFailures));
        Thread nodeB = new Thread(() -> recoverOnNode(recoveryNodeB, recoveredCounts, recoveryFailures));

        nodeA.start();
        nodeB.start();
        nodeA.join(2000);
        nodeB.join(2000);

        assertThat(recoveredCounts).contains(1);
        assertThat(recoveredCounts.stream().mapToInt(Integer::intValue).sum()).isEqualTo(1);
        assertThat(recoveryFailures)
                .allMatch(org.springframework.dao.OptimisticLockingFailureException.class::isInstance);
        assertThat(producer.queueSize()).isEqualTo(1);

        DefaultActionExecutionCallback callbackNodeA = new DefaultActionExecutionCallback(
                actionInstanceRepository,
                actionStepInstanceRepository,
                definitionRegistry,
                new StepHandlerRegistry(List.of(new SuccessHandler("MQ_MESSAGE"), new SuccessHandler("SMS"))),
                new FixedAttemptActionRetryPolicy(0),
                actionOutboxRepository,
                Optional.of(producer),
                clock
        );
        DefaultActionExecutionCallback callbackNodeB = new DefaultActionExecutionCallback(
                actionInstanceRepository,
                actionStepInstanceRepository,
                definitionRegistry,
                new StepHandlerRegistry(List.of(new SuccessHandler("MQ_MESSAGE"), new SuccessHandler("SMS"))),
                new FixedAttemptActionRetryPolicy(0),
                actionOutboxRepository,
                Optional.of(producer),
                clock
        );

        callbackNodeA.execute(producer.take());
        assertThat(producer.queueSize()).isEqualTo(1);

        callbackNodeB.execute(producer.take());

        ActionInstance finalState = actionInstanceRepository.findByActionNameAndBizKey("order-cancel-flow", "order:1").orElseThrow();
        assertThat(finalState.status()).isEqualTo(ActionStatus.SUCCESS);
        assertThat(finalState.currentStepIndex()).isEqualTo(2);
        assertThat(actionStepInstanceRepository.findByActionInstanceId(finalState.id()))
                .extracting(step -> step.status())
                .containsExactly(ActionStepStatus.SUCCESS, ActionStepStatus.SUCCESS);
        assertThat(producer.publishedMessages()).hasSize(2);
        assertThat(producer.queueSize()).isZero();
    }

    private void recoverOnNode(
            ActionOutboxRecoveryService recoveryService,
            List<Integer> recoveredCounts,
            List<Throwable> recoveryFailures
    ) {
        try {
            recoveredCounts.add(recoveryService.recoverDueOutboxes(10, Duration.ofSeconds(30)));
        } catch (Throwable ex) {
            recoveryFailures.add(ex);
        }
    }

    private static final class SuccessHandler implements ActionStepHandler {
        private final String stepType;

        private SuccessHandler(String stepType) {
            this.stepType = stepType;
        }

        @Override
        public String stepType() {
            return stepType;
        }

        @Override
        public StepExecutionResult execute(ActionStepContext context) {
            return StepExecutionResult.succeeded();
        }
    }

    private static final class QueueingMessageProducer implements ActionExecutionMessageProducer {
        private final ActionExecutionMessageFactory messageFactory = new ActionExecutionMessageFactory();
        private final BlockingQueue<ActionExecutionMessage> queue = new LinkedBlockingQueue<>();
        private final List<ActionExecutionMessage> publishedMessages = new ArrayList<>();

        @Override
        public void publish(io.github.actionguard.core.model.ActionOutbox outbox) {
            ActionExecutionMessage message = messageFactory.create(outbox);
            publishedMessages.add(message);
            queue.add(message);
        }

        private ActionExecutionMessage take() throws InterruptedException {
            ActionExecutionMessage message = queue.poll(2, TimeUnit.SECONDS);
            if (message == null) {
                throw new IllegalStateException("expected queued message");
            }
            return message;
        }

        private int queueSize() {
            return queue.size();
        }

        private List<ActionExecutionMessage> publishedMessages() {
            return List.copyOf(publishedMessages);
        }
    }

    private static final class CoordinatedActionOutboxRepository extends InMemoryActionOutboxRepository {
        private final CountDownLatch readersReady;
        private final CountDownLatch releaseReaders = new CountDownLatch(1);
        private final AtomicInteger coordinationBudget = new AtomicInteger(2);

        private CoordinatedActionOutboxRepository(int concurrentReaders) {
            this.readersReady = new CountDownLatch(concurrentReaders);
        }

        @Override
        public List<io.github.actionguard.core.model.ActionOutbox> findRecoverable(Instant availableBeforeOrAt, Instant claimedBeforeOrAt, int limit) {
            List<io.github.actionguard.core.model.ActionOutbox> recoverable = super.findRecoverable(availableBeforeOrAt, claimedBeforeOrAt, limit);
            if (!recoverable.isEmpty() && coordinationBudget.getAndDecrement() > 0) {
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
