package io.github.actionguard.core.runtime;

import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.api.runtime.ActionRetryAction;
import io.github.actionguard.api.runtime.ActionRetryContext;
import io.github.actionguard.api.runtime.ActionStepContext;
import io.github.actionguard.api.runtime.StepExecutionResult;
import io.github.actionguard.api.spi.ActionRetryPolicy;
import io.github.actionguard.api.spi.ActionStepHandler;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.model.ActionStepStatus;
import io.github.actionguard.core.repository.InMemoryActionInstanceRepository;
import io.github.actionguard.core.repository.InMemoryActionOutboxRepository;
import io.github.actionguard.core.repository.InMemoryActionStepInstanceRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultActionExecutionCallbackTest {

    @Test
    void shouldAdvanceToNextStepAfterSuccessfulExecution() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        CapturingActionExecutionMessageProducer producer = new CapturingActionExecutionMessageProducer();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.NEW, 0, 2, Map.of("operator", "demo"),
                null, null, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z")
        ));
        actionStepInstanceRepository.saveAll(List.of(
                new ActionStepInstance("step-1", "act-1", 0, "send-cancel-event", "MQ_MESSAGE", "order.cancel.exchange", ActionStepStatus.PENDING, 0, Map.of("orderId", "1"), null, null, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z")),
                new ActionStepInstance("step-2", "act-1", 1, "send-user-sms", "SMS", "notify.user", ActionStepStatus.PENDING, 0, Map.of("template", "order-cancel"), null, null, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z"))
        ));
        actionOutboxRepository.save(new ActionOutbox(
                "outbox-1", "act-1", "ACTION_EXECUTE", ActionOutboxStatus.NEW, Instant.parse("2026-06-26T09:00:00Z"),
                0, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z")
        ));
        DefaultActionExecutionCallback callback = new DefaultActionExecutionCallback(
                actionInstanceRepository,
                actionStepInstanceRepository,
                new StepHandlerRegistry(List.of(new SuccessHandler("MQ_MESSAGE"))),
                new RetryCurrentStepPolicy(3),
                actionOutboxRepository,
                Optional.of(producer),
                Clock.fixed(Instant.parse("2026-06-26T09:01:00Z"), ZoneOffset.UTC)
        );

        callback.execute(new ActionExecutionMessage("ACTION_EXECUTE:outbox-1", "ACTION_EXECUTE:act-1", "outbox-1", "act-1", "ACTION_EXECUTE", Instant.parse("2026-06-26T09:00:00Z")));

        ActionInstance updated = actionInstanceRepository.findById("act-1").orElseThrow();
        assertThat(updated.status()).isEqualTo(ActionStatus.DISPATCHING);
        assertThat(updated.currentStepIndex()).isEqualTo(1);
        assertThat(actionStepInstanceRepository.findByActionInstanceId("act-1").get(0).status()).isEqualTo(ActionStepStatus.SUCCESS);
        assertThat(producer.published()).hasSize(1);
        assertThat(producer.published().get(0).id()).isEqualTo("outbox-1");
    }

    @Test
    void shouldMarkActionSuccessAfterFinalStep() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        CapturingActionExecutionMessageProducer producer = new CapturingActionExecutionMessageProducer();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.NEW, 0, 1, Map.of(),
                null, null, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z")
        ));
        actionStepInstanceRepository.save(new ActionStepInstance(
                "step-1", "act-1", 0, "send-user-sms", "SMS", "notify.user", ActionStepStatus.PENDING, 0, Map.of(), null, null,
                0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z")
        ));
        actionOutboxRepository.save(new ActionOutbox(
                "outbox-1", "act-1", "ACTION_EXECUTE", ActionOutboxStatus.NEW, Instant.parse("2026-06-26T09:00:00Z"),
                0, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z")
        ));
        DefaultActionExecutionCallback callback = new DefaultActionExecutionCallback(
                actionInstanceRepository,
                actionStepInstanceRepository,
                new StepHandlerRegistry(List.of(new SuccessHandler("SMS"))),
                new RetryCurrentStepPolicy(3),
                actionOutboxRepository,
                Optional.of(producer),
                Clock.fixed(Instant.parse("2026-06-26T09:01:00Z"), ZoneOffset.UTC)
        );

        callback.execute(new ActionExecutionMessage("ACTION_EXECUTE:outbox-1", "ACTION_EXECUTE:act-1", "outbox-1", "act-1", "ACTION_EXECUTE", Instant.parse("2026-06-26T09:00:00Z")));

        ActionInstance updated = actionInstanceRepository.findById("act-1").orElseThrow();
        assertThat(updated.status()).isEqualTo(ActionStatus.SUCCESS);
        assertThat(updated.currentStepIndex()).isEqualTo(1);
        assertThat(producer.published()).isEmpty();
    }

    @Test
    void shouldMarkActionRetryingAndCaptureErrorWhenStepFails() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        CapturingActionExecutionMessageProducer producer = new CapturingActionExecutionMessageProducer();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.NEW, 0, 1, Map.of(),
                null, null, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z")
        ));
        actionStepInstanceRepository.save(new ActionStepInstance(
                "step-1", "act-1", 0, "send-user-sms", "SMS", "notify.user", ActionStepStatus.PENDING, 0, Map.of(), null, null,
                0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z")
        ));
        actionOutboxRepository.save(new ActionOutbox(
                "outbox-1", "act-1", "ACTION_EXECUTE", ActionOutboxStatus.NEW, Instant.parse("2026-06-26T09:00:00Z"),
                0, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z")
        ));
        DefaultActionExecutionCallback callback = new DefaultActionExecutionCallback(
                actionInstanceRepository,
                actionStepInstanceRepository,
                new StepHandlerRegistry(List.of(new FailingHandler("SMS"))),
                new RetryCurrentStepPolicy(3),
                actionOutboxRepository,
                Optional.of(producer),
                Clock.fixed(Instant.parse("2026-06-26T09:01:00Z"), ZoneOffset.UTC)
        );

        callback.execute(new ActionExecutionMessage("ACTION_EXECUTE:outbox-1", "ACTION_EXECUTE:act-1", "outbox-1", "act-1", "ACTION_EXECUTE", Instant.parse("2026-06-26T09:00:00Z")));

        ActionInstance updated = actionInstanceRepository.findById("act-1").orElseThrow();
        ActionStepInstance step = actionStepInstanceRepository.findByActionInstanceId("act-1").get(0);
        assertThat(updated.status()).isEqualTo(ActionStatus.RETRYING);
        assertThat(updated.lastErrorCode()).isEqualTo("DOWNSTREAM_ERROR");
        assertThat(updated.lastErrorMessage()).isEqualTo("sms provider failed");
        assertThat(step.status()).isEqualTo(ActionStepStatus.FAILED);
        assertThat(step.lastErrorCode()).isEqualTo("DOWNSTREAM_ERROR");
        assertThat(producer.published()).hasSize(1);
        assertThat(producer.published().get(0).id()).isEqualTo("outbox-1");
    }

    @Test
    void shouldStopRetryingWhenRetryPolicyReturnsDead() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        CapturingActionExecutionMessageProducer producer = new CapturingActionExecutionMessageProducer();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.NEW, 0, 1, Map.of(),
                null, null, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z")
        ));
        actionStepInstanceRepository.save(new ActionStepInstance(
                "step-1", "act-1", 0, "send-user-sms", "SMS", "notify.user", ActionStepStatus.PENDING, 3, Map.of(), null, null,
                0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z")
        ));
        actionOutboxRepository.save(new ActionOutbox(
                "outbox-1", "act-1", "ACTION_EXECUTE", ActionOutboxStatus.NEW, Instant.parse("2026-06-26T09:00:00Z"),
                0, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z")
        ));
        DefaultActionExecutionCallback callback = new DefaultActionExecutionCallback(
                actionInstanceRepository,
                actionStepInstanceRepository,
                new StepHandlerRegistry(List.of(new FailingHandler("SMS"))),
                new RetryCurrentStepPolicy(3),
                actionOutboxRepository,
                Optional.of(producer),
                Clock.fixed(Instant.parse("2026-06-26T09:01:00Z"), ZoneOffset.UTC)
        );

        callback.execute(new ActionExecutionMessage("ACTION_EXECUTE:outbox-1", "ACTION_EXECUTE:act-1", "outbox-1", "act-1", "ACTION_EXECUTE", Instant.parse("2026-06-26T09:00:00Z")));

        ActionInstance updated = actionInstanceRepository.findById("act-1").orElseThrow();
        assertThat(updated.status()).isEqualTo(ActionStatus.FAILED);
        assertThat(producer.published()).isEmpty();
    }

    @Test
    void shouldLeaveRetryingStateAfterRetrySucceeds() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        CapturingActionExecutionMessageProducer producer = new CapturingActionExecutionMessageProducer();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.NEW, 0, 1, Map.of(),
                null, null, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z")
        ));
        actionStepInstanceRepository.save(new ActionStepInstance(
                "step-1", "act-1", 0, "send-user-sms", "SMS", "notify.user", ActionStepStatus.PENDING, 0, Map.of(), null, null,
                0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z")
        ));
        actionOutboxRepository.save(new ActionOutbox(
                "outbox-1", "act-1", "ACTION_EXECUTE", ActionOutboxStatus.NEW, Instant.parse("2026-06-26T09:00:00Z"),
                0, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z")
        ));
        FlakyHandler flakyHandler = new FlakyHandler("SMS");
        DefaultActionExecutionCallback callback = new DefaultActionExecutionCallback(
                actionInstanceRepository,
                actionStepInstanceRepository,
                new StepHandlerRegistry(List.of(flakyHandler)),
                new RetryCurrentStepPolicy(3),
                actionOutboxRepository,
                Optional.of(producer),
                Clock.fixed(Instant.parse("2026-06-26T09:01:00Z"), ZoneOffset.UTC)
        );

        callback.execute(new ActionExecutionMessage("ACTION_EXECUTE:outbox-1", "ACTION_EXECUTE:act-1", "outbox-1", "act-1", "ACTION_EXECUTE", Instant.parse("2026-06-26T09:00:00Z")));
        assertThat(actionInstanceRepository.findById("act-1").orElseThrow().status()).isEqualTo(ActionStatus.RETRYING);

        callback.execute(new ActionExecutionMessage("ACTION_EXECUTE:outbox-1", "ACTION_EXECUTE:act-1", "outbox-1", "act-1", "ACTION_EXECUTE", Instant.parse("2026-06-26T09:00:00Z")));

        ActionInstance updated = actionInstanceRepository.findById("act-1").orElseThrow();
        assertThat(updated.status()).isEqualTo(ActionStatus.SUCCESS);
        assertThat(updated.lastErrorCode()).isNull();
        assertThat(updated.lastErrorMessage()).isNull();
    }

    private static final class CapturingActionExecutionMessageProducer implements ActionExecutionMessageProducer {
        private final List<ActionOutbox> published = new ArrayList<>();

        @Override
        public void publish(ActionOutbox outbox) {
            published.add(outbox);
        }

        private List<ActionOutbox> published() {
            return List.copyOf(published);
        }
    }

    private record RetryCurrentStepPolicy(int maxRetryCount) implements ActionRetryPolicy {
        @Override
        public ActionRetryAction decide(Throwable throwable, ActionRetryContext context) {
            return context.retryable() && context.currentRetryCount() < maxRetryCount
                    ? ActionRetryAction.IMMEDIATE_RETRY
                    : ActionRetryAction.DEAD;
        }
    }

    private record SuccessHandler(String stepType) implements ActionStepHandler {
        @Override
        public StepExecutionResult execute(ActionStepContext context) {
            return StepExecutionResult.succeeded();
        }
    }

    private record FailingHandler(String stepType) implements ActionStepHandler {
        @Override
        public StepExecutionResult execute(ActionStepContext context) {
            return StepExecutionResult.failed("DOWNSTREAM_ERROR", "sms provider failed");
        }
    }

    private static final class FlakyHandler implements ActionStepHandler {
        private final String stepType;
        private int attempts;

        private FlakyHandler(String stepType) {
            this.stepType = stepType;
        }

        @Override
        public String stepType() {
            return stepType;
        }

        @Override
        public StepExecutionResult execute(ActionStepContext context) {
            attempts++;
            if (attempts == 1) {
                return StepExecutionResult.failed("DOWNSTREAM_ERROR", "sms provider failed");
            }
            return StepExecutionResult.succeeded();
        }
    }
}
