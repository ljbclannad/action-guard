package io.github.actionguard.core.runtime.execution;

import io.github.actionguard.core.runtime.definition.InMemoryActionDefinitionRegistry;
import io.github.actionguard.core.runtime.definition.ActionDefinitionRegistry;
import io.github.actionguard.core.runtime.definition.ActionDefinitionValidator;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import io.github.actionguard.core.runtime.recovery.ActionOutboxRecoveryService;
import io.github.actionguard.core.runtime.registry.StepHandlerRegistry;
import io.github.actionguard.core.runtime.retry.FixedAttemptActionRetryPolicy;
import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.api.runtime.ActionRetryAction;
import io.github.actionguard.api.runtime.ActionRetryContext;
import io.github.actionguard.api.runtime.ActionStepContext;
import io.github.actionguard.api.runtime.StepExecutionResult;
import io.github.actionguard.api.spi.ActionRetryPolicy;
import io.github.actionguard.api.spi.ActionStepHandler;
import io.github.actionguard.api.definition.ActionDefinition;
import io.github.actionguard.api.definition.ActionStepDefinition;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.model.ActionStepStatus;
import io.github.actionguard.core.model.ActionTransitionLog;
import io.github.actionguard.core.repository.InMemoryActionInstanceRepository;
import io.github.actionguard.core.repository.InMemoryActionOutboxRepository;
import io.github.actionguard.core.repository.InMemoryActionStepInstanceRepository;
import io.github.actionguard.core.repository.InMemoryActionTransitionLogRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultActionExecutionCallbackTest {

    @Test
    void shouldUseProvidedTransitionLogRepositoryInCompatibilityConstructor() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        InMemoryActionTransitionLogRepository transitionLogRepository = new InMemoryActionTransitionLogRepository();
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
                definitionRegistry("order-cancel-flow", List.of(stepDefinition("send-user-sms", "SMS"))),
                new StepHandlerRegistry(List.of(new SuccessHandler("SMS"))),
                new RetryCurrentStepPolicy(3),
                actionOutboxRepository,
                transitionLogRepository,
                Optional.of(producer),
                Clock.fixed(Instant.parse("2026-06-26T09:01:00Z"), ZoneOffset.UTC)
        );

        callback.execute(new ActionExecutionMessage("ACTION_EXECUTE:outbox-1", "ACTION_EXECUTE:act-1", "outbox-1", "act-1", "ACTION_EXECUTE", Instant.parse("2026-06-26T09:00:00Z")));

        List<ActionTransitionLog> logs = transitionLogRepository.findByActionInstanceId("act-1");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).toStatus()).isEqualTo(ActionStatus.SUCCESS);
    }

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
                definitionRegistry("order-cancel-flow", List.of(
                        stepDefinition("send-cancel-event", "MQ_MESSAGE"),
                        stepDefinition("send-user-sms", "SMS")
                )),
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
        CapturingMetricsRecorder metricsRecorder = new CapturingMetricsRecorder();
        DefaultActionExecutionCallback callback = new DefaultActionExecutionCallback(
                actionInstanceRepository,
                actionStepInstanceRepository,
                definitionRegistry("order-cancel-flow", List.of(stepDefinition("send-user-sms", "SMS"))),
                new StepHandlerRegistry(List.of(new SuccessHandler("SMS"))),
                new RetryCurrentStepPolicy(3),
                actionOutboxRepository,
                Optional.of(producer),
                new ActionObservabilityService(Optional.empty(), Optional.of(metricsRecorder), Clock.fixed(Instant.parse("2026-06-26T09:01:00Z"), ZoneOffset.UTC)),
                Clock.fixed(Instant.parse("2026-06-26T09:01:00Z"), ZoneOffset.UTC)
        );

        callback.execute(new ActionExecutionMessage("ACTION_EXECUTE:outbox-1", "ACTION_EXECUTE:act-1", "outbox-1", "act-1", "ACTION_EXECUTE", Instant.parse("2026-06-26T09:00:00Z")));

        ActionInstance updated = actionInstanceRepository.findById("act-1").orElseThrow();
        assertThat(updated.status()).isEqualTo(ActionStatus.SUCCESS);
        assertThat(updated.currentStepIndex()).isEqualTo(1);
        assertThat(producer.published()).isEmpty();
        assertThat(metricsRecorder.counters)
                .containsEntry("action.guard.step.succeeded|{actionName=order-cancel-flow, result=success, stepType=SMS}", 1L)
                .containsEntry("action.guard.action.succeeded|{actionName=order-cancel-flow, result=success, stepType=SMS}", 1L);
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
                definitionRegistry("order-cancel-flow", List.of(stepDefinition("send-user-sms", "SMS"))),
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
    void shouldPersistFailureWhenHandlerThrowsException() {
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
                definitionRegistry("order-cancel-flow", List.of(stepDefinition("send-user-sms", "SMS"))),
                new StepHandlerRegistry(List.of(new ThrowingHandler("SMS"))),
                new RetryCurrentStepPolicy(3),
                actionOutboxRepository,
                Optional.of(producer),
                Clock.fixed(Instant.parse("2026-06-26T09:01:00Z"), ZoneOffset.UTC)
        );

        callback.execute(new ActionExecutionMessage("ACTION_EXECUTE:outbox-1", "ACTION_EXECUTE:act-1", "outbox-1", "act-1", "ACTION_EXECUTE", Instant.parse("2026-06-26T09:00:00Z")));

        ActionInstance updated = actionInstanceRepository.findById("act-1").orElseThrow();
        ActionStepInstance step = actionStepInstanceRepository.findByActionInstanceId("act-1").get(0);
        assertThat(updated.status()).isEqualTo(ActionStatus.RETRYING);
        assertThat(updated.lastErrorCode()).isEqualTo("STEP_EXECUTION_EXCEPTION");
        assertThat(updated.lastErrorMessage()).isEqualTo("payload missing");
        assertThat(step.status()).isEqualTo(ActionStepStatus.FAILED);
        assertThat(step.lastErrorCode()).isEqualTo("STEP_EXECUTION_EXCEPTION");
        assertThat(step.lastErrorMessage()).isEqualTo("payload missing");
        assertThat(producer.published()).hasSize(1);
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
                definitionRegistry("order-cancel-flow", List.of(new ActionStepDefinition("send-user-sms", "SMS", "notify.user", 3, null, null))),
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
                definitionRegistry("order-cancel-flow", List.of(stepDefinition("send-user-sms", "SMS"))),
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

    @Test
    void shouldStopNextStepDispatchWhenActionVersionConflictsAfterStepSuccess() {
        ConflictActionInstanceRepository actionInstanceRepository = new ConflictActionInstanceRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        CapturingActionExecutionMessageProducer producer = new CapturingActionExecutionMessageProducer();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.NEW, 0, 2, Map.of(),
                null, null, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z")
        ));
        actionStepInstanceRepository.saveAll(List.of(
                new ActionStepInstance("step-1", "act-1", 0, "send-cancel-event", "MQ_MESSAGE", "order.cancel.exchange", ActionStepStatus.PENDING, 0, Map.of(), null, null, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z")),
                new ActionStepInstance("step-2", "act-1", 1, "send-user-sms", "SMS", "notify.user", ActionStepStatus.PENDING, 0, Map.of(), null, null, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z"))
        ));
        actionOutboxRepository.save(new ActionOutbox(
                "outbox-1", "act-1", "ACTION_EXECUTE", ActionOutboxStatus.NEW, Instant.parse("2026-06-26T09:00:00Z"),
                0, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z")
        ));
        actionInstanceRepository.forceNextConflict();
        DefaultActionExecutionCallback callback = new DefaultActionExecutionCallback(
                actionInstanceRepository,
                actionStepInstanceRepository,
                definitionRegistry("order-cancel-flow", List.of(
                        stepDefinition("send-cancel-event", "MQ_MESSAGE"),
                        stepDefinition("send-user-sms", "SMS")
                )),
                new StepHandlerRegistry(List.of(new SuccessHandler("MQ_MESSAGE"))),
                new RetryCurrentStepPolicy(3),
                actionOutboxRepository,
                Optional.of(producer),
                Clock.fixed(Instant.parse("2026-06-26T09:01:00Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> callback.execute(new ActionExecutionMessage("ACTION_EXECUTE:outbox-1", "ACTION_EXECUTE:act-1", "outbox-1", "act-1", "ACTION_EXECUTE", Instant.parse("2026-06-26T09:00:00Z"))))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        assertThat(producer.published()).isEmpty();
    }

    @Test
    void shouldStopRetryDispatchWhenActionVersionConflictsAfterStepFailure() {
        ConflictActionInstanceRepository actionInstanceRepository = new ConflictActionInstanceRepository();
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
        actionInstanceRepository.forceNextConflict();
        DefaultActionExecutionCallback callback = new DefaultActionExecutionCallback(
                actionInstanceRepository,
                actionStepInstanceRepository,
                definitionRegistry("order-cancel-flow", List.of(stepDefinition("send-user-sms", "SMS"))),
                new StepHandlerRegistry(List.of(new FailingHandler("SMS"))),
                new RetryCurrentStepPolicy(3),
                actionOutboxRepository,
                Optional.of(producer),
                Clock.fixed(Instant.parse("2026-06-26T09:01:00Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> callback.execute(new ActionExecutionMessage("ACTION_EXECUTE:outbox-1", "ACTION_EXECUTE:act-1", "outbox-1", "act-1", "ACTION_EXECUTE", Instant.parse("2026-06-26T09:00:00Z"))))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        assertThat(producer.published()).isEmpty();
    }

    @Test
    void shouldDelayRetryUsingStepBackoff() {
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
                definitionRegistry("order-cancel-flow", List.of(new ActionStepDefinition("send-user-sms", "SMS", "notify.user", 3, 5000L, null))),
                new StepHandlerRegistry(List.of(new FailingHandler("SMS"))),
                new DelayRetryPolicy(),
                actionOutboxRepository,
                Optional.of(producer),
                Clock.fixed(Instant.parse("2026-06-26T09:01:00Z"), ZoneOffset.UTC)
        );

        callback.execute(new ActionExecutionMessage("ACTION_EXECUTE:outbox-1", "ACTION_EXECUTE:act-1", "outbox-1", "act-1", "ACTION_EXECUTE", Instant.parse("2026-06-26T09:00:00Z")));

        assertThat(actionInstanceRepository.findById("act-1").orElseThrow().status()).isEqualTo(ActionStatus.RETRYING);
        assertThat(actionOutboxRepository.findByActionInstanceId("act-1").orElseThrow().availableAt())
                .isEqualTo(Instant.parse("2026-06-26T09:01:05Z"));
        assertThat(actionOutboxRepository.findByActionInstanceId("act-1").orElseThrow().status()).isEqualTo(ActionOutboxStatus.NEW);
        assertThat(producer.published()).isEmpty();
    }

    @Test
    void shouldTreatOvertimeStepAsTimeoutFailure() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        CapturingActionExecutionMessageProducer producer = new CapturingActionExecutionMessageProducer();
        MutableClock clock = new MutableClock(Instant.parse("2026-06-26T09:01:00Z"));
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
        CapturingMetricsRecorder metricsRecorder = new CapturingMetricsRecorder();
        DefaultActionExecutionCallback callback = new DefaultActionExecutionCallback(
                actionInstanceRepository,
                actionStepInstanceRepository,
                definitionRegistry("order-cancel-flow", List.of(new ActionStepDefinition("send-user-sms", "SMS", "notify.user", 0, null, 1000L))),
                new StepHandlerRegistry(List.of(new AdvancingSuccessHandler("SMS", clock, 2000L))),
                new RetryCurrentStepPolicy(0),
                actionOutboxRepository,
                Optional.of(producer),
                new ActionObservabilityService(Optional.empty(), Optional.of(metricsRecorder), clock),
                clock
        );

        callback.execute(new ActionExecutionMessage("ACTION_EXECUTE:outbox-1", "ACTION_EXECUTE:act-1", "outbox-1", "act-1", "ACTION_EXECUTE", Instant.parse("2026-06-26T09:00:00Z")));

        ActionInstance updated = actionInstanceRepository.findById("act-1").orElseThrow();
        ActionStepInstance step = actionStepInstanceRepository.findByActionInstanceId("act-1").get(0);
        assertThat(updated.status()).isEqualTo(ActionStatus.FAILED);
        assertThat(updated.lastErrorCode()).isEqualTo("STEP_TIMEOUT");
        assertThat(step.lastErrorCode()).isEqualTo("STEP_TIMEOUT");
        assertThat(step.lastErrorMessage()).contains("timed out");
        assertThat(metricsRecorder.counters)
                .containsEntry("action.guard.step.timed_out|{actionName=order-cancel-flow, result=timeout, stepType=SMS}", 1L)
                .containsEntry("action.guard.step.failed|{actionName=order-cancel-flow, errorCode=STEP_TIMEOUT, result=failed, stepType=SMS}", 1L)
                .containsEntry("action.guard.action.failed|{actionName=order-cancel-flow, errorCode=STEP_TIMEOUT, result=failed, stepType=SMS}", 1L);
    }

    @Test
    void shouldRecoverDispatchAfterProducerTemporaryFailure() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        RetryOnceProducer producer = new RetryOnceProducer();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.NEW, 0, 2, Map.of(),
                null, null, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z")
        ));
        actionStepInstanceRepository.saveAll(List.of(
                new ActionStepInstance("step-1", "act-1", 0, "send-cancel-event", "MQ_MESSAGE", "order.cancel.exchange", ActionStepStatus.PENDING, 0, Map.of(), null, null, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z")),
                new ActionStepInstance("step-2", "act-1", 1, "send-user-sms", "SMS", "notify.user", ActionStepStatus.PENDING, 0, Map.of(), null, null, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z"))
        ));
        actionOutboxRepository.save(new ActionOutbox(
                "outbox-1", "act-1", "ACTION_EXECUTE", ActionOutboxStatus.NEW, Instant.parse("2026-06-26T09:00:00Z"),
                0, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:00:00Z")
        ));
        Clock clock = Clock.fixed(Instant.parse("2026-06-26T09:01:00Z"), ZoneOffset.UTC);
        DefaultActionExecutionCallback callback = new DefaultActionExecutionCallback(
                actionInstanceRepository,
                actionStepInstanceRepository,
                definitionRegistry("order-cancel-flow", List.of(
                        stepDefinition("send-cancel-event", "MQ_MESSAGE"),
                        stepDefinition("send-user-sms", "SMS")
                )),
                new StepHandlerRegistry(List.of(new SuccessHandler("MQ_MESSAGE"))),
                new RetryCurrentStepPolicy(3),
                actionOutboxRepository,
                Optional.of(producer),
                clock
        );

        callback.execute(new ActionExecutionMessage("ACTION_EXECUTE:outbox-1", "ACTION_EXECUTE:act-1", "outbox-1", "act-1", "ACTION_EXECUTE", Instant.parse("2026-06-26T09:00:00Z")));

        assertThat(actionOutboxRepository.findByActionInstanceId("act-1").orElseThrow().status()).isEqualTo(ActionOutboxStatus.NEW);
        ActionOutboxRecoveryService recoveryService = new ActionOutboxRecoveryService(
                actionOutboxRepository,
                Optional.of(producer),
                new ActionObservabilityService(Optional.empty(), Optional.empty(), clock),
                clock
        );

        int recovered = recoveryService.recoverDueOutboxes(10, java.time.Duration.ofSeconds(30));

        assertThat(recovered).isEqualTo(1);
        assertThat(producer.published()).hasSize(1);
        assertThat(actionOutboxRepository.findByActionInstanceId("act-1").orElseThrow().status()).isEqualTo(ActionOutboxStatus.DONE);
    }

    @Test
    void shouldIgnoreDuplicateExecutionWhenActionAlreadyTerminal() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        CountingSuccessHandler handler = new CountingSuccessHandler("SMS");
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.SUCCESS, 1, 1, Map.of(),
                null, null, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:01:00Z")
        ));
        actionStepInstanceRepository.save(new ActionStepInstance(
                "step-1", "act-1", 0, "send-user-sms", "SMS", "notify.user", ActionStepStatus.SUCCESS, 1, Map.of(), null, null,
                0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:01:00Z")
        ));
        actionOutboxRepository.save(new ActionOutbox(
                "outbox-1", "act-1", "ACTION_EXECUTE", ActionOutboxStatus.DONE, Instant.parse("2026-06-26T09:00:00Z"),
                0, 0, Instant.parse("2026-06-26T09:00:00Z"), Instant.parse("2026-06-26T09:01:00Z")
        ));
        DefaultActionExecutionCallback callback = new DefaultActionExecutionCallback(
                actionInstanceRepository,
                actionStepInstanceRepository,
                definitionRegistry("order-cancel-flow", List.of(stepDefinition("send-user-sms", "SMS"))),
                new StepHandlerRegistry(List.of(handler)),
                new RetryCurrentStepPolicy(3),
                actionOutboxRepository,
                Optional.empty(),
                Clock.fixed(Instant.parse("2026-06-26T09:02:00Z"), ZoneOffset.UTC)
        );

        callback.execute(new ActionExecutionMessage("ACTION_EXECUTE:outbox-1", "ACTION_EXECUTE:act-1", "outbox-1", "act-1", "ACTION_EXECUTE", Instant.parse("2026-06-26T09:00:00Z")));

        assertThat(handler.invocationCount()).isZero();
    }

    private static ActionDefinitionRegistry definitionRegistry(String actionName, List<ActionStepDefinition> steps) {
        return new InMemoryActionDefinitionRegistry(
                List.of(new ActionDefinition(actionName, "demo", false, steps)),
                new ActionDefinitionValidator()
        );
    }

    private static ActionStepDefinition stepDefinition(String stepName, String stepType) {
        return new ActionStepDefinition(stepName, stepType, "target:" + stepName, null, null, null);
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

    private static final class DelayRetryPolicy implements ActionRetryPolicy {
        @Override
        public ActionRetryAction decide(Throwable throwable, ActionRetryContext context) {
            return ActionRetryAction.DELAY_RETRY;
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

    private record ThrowingHandler(String stepType) implements ActionStepHandler {
        @Override
        public StepExecutionResult execute(ActionStepContext context) {
            throw new IllegalArgumentException("payload missing");
        }
    }

    private static final class CapturingMetricsRecorder implements io.github.actionguard.api.spi.ActionMetricsRecorder {
        private final java.util.LinkedHashMap<String, Long> counters = new java.util.LinkedHashMap<>();

        @Override
        public void increment(String metricName, Map<String, String> tags) {
            String key = metricName + "|" + new java.util.TreeMap<>(tags);
            counters.merge(key, 1L, Long::sum);
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

    private static final class RetryOnceProducer implements ActionExecutionMessageProducer {
        private final List<ActionOutbox> published = new ArrayList<>();
        private boolean failOnce = true;

        @Override
        public void publish(ActionOutbox outbox) {
            if (failOnce) {
                failOnce = false;
                throw new IllegalStateException("simulated dispatch failure");
            }
            published.add(outbox);
        }

        private List<ActionOutbox> published() {
            return List.copyOf(published);
        }
    }

    private static final class CountingSuccessHandler implements ActionStepHandler {
        private final String stepType;
        private int invocationCount;

        private CountingSuccessHandler(String stepType) {
            this.stepType = stepType;
        }

        @Override
        public String stepType() {
            return stepType;
        }

        @Override
        public StepExecutionResult execute(ActionStepContext context) {
            invocationCount++;
            return StepExecutionResult.succeeded();
        }

        private int invocationCount() {
            return invocationCount;
        }
    }

    private static final class AdvancingSuccessHandler implements ActionStepHandler {
        private final String stepType;
        private final MutableClock clock;
        private final long durationMillis;

        private AdvancingSuccessHandler(String stepType, MutableClock clock, long durationMillis) {
            this.stepType = stepType;
            this.clock = clock;
            this.durationMillis = durationMillis;
        }

        @Override
        public String stepType() {
            return stepType;
        }

        @Override
        public StepExecutionResult execute(ActionStepContext context) {
            clock.advanceMillis(durationMillis);
            return StepExecutionResult.succeeded();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void advanceMillis(long millis) {
            current = current.plusMillis(millis);
        }
    }

    private static final class ConflictActionInstanceRepository extends InMemoryActionInstanceRepository {
        private boolean forceNextConflict;

        private void forceNextConflict() {
            this.forceNextConflict = true;
        }

        @Override
        public ActionInstance save(ActionInstance instance) {
            if (forceNextConflict) {
                forceNextConflict = false;
                throw new org.springframework.dao.OptimisticLockingFailureException("forced conflict");
            }
            return super.save(instance);
        }
    }
}
