package io.github.actionguard.ops.api.service;

import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.model.ActionStepStatus;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.runtime.definition.ActionDefinitionRegistry;
import io.github.actionguard.core.runtime.definition.ActionDefinitionValidator;
import io.github.actionguard.core.repository.InMemoryActionInstanceRepository;
import io.github.actionguard.core.repository.InMemoryActionOutboxRepository;
import io.github.actionguard.core.repository.InMemoryActionStepInstanceRepository;
import io.github.actionguard.core.runtime.compensation.ActionCompensationExecutor;
import io.github.actionguard.core.runtime.execution.ActionExecutionCallback;
import io.github.actionguard.ops.api.support.ActionCommandValidator;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import io.github.actionguard.core.runtime.execution.DefaultActionExecutionCallback;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import io.github.actionguard.core.runtime.definition.InMemoryActionDefinitionRegistry;
import io.github.actionguard.core.runtime.registry.StepHandlerRegistry;
import io.github.actionguard.core.runtime.retry.FixedAttemptActionRetryPolicy;
import io.github.actionguard.ops.api.repository.ActionAuditLogRepository;
import io.github.actionguard.ops.api.repository.jdbc.InMemoryAuditLogRepository;
import io.github.actionguard.api.definition.ActionDefinition;
import io.github.actionguard.api.definition.ActionStepDefinition;
import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.api.runtime.ActionStepContext;
import io.github.actionguard.api.runtime.StepExecutionResult;
import io.github.actionguard.api.spi.ActionStepHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionCommandServiceTest {

    @Test
    void shouldRejectRetryWhenActionStatusIsSuccess() {
        ActionCommandValidator validator = new ActionCommandValidator();
        assertThatThrownBy(() -> validator.validateRetry(ActionStatus.SUCCESS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Retry is not allowed");
    }

    @Test
    void shouldRetryFailedActionAndWriteAuditLog() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        ActionAuditLogRepository auditLogRepository = InMemoryAuditLogRepository.create();
        CapturingProducer producer = new CapturingProducer();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.FAILED, 0, 1, Map.of(),
                "DOWNSTREAM_ERROR", "sms provider failed", 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));
        actionOutboxRepository.save(new ActionOutbox(
                "outbox-1", "act-1", "ACTION_EXECUTE", ActionOutboxStatus.NEW, Instant.parse("2026-06-26T12:00:00Z"),
                0, 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));
        CapturingMetricsRecorder metricsRecorder = new CapturingMetricsRecorder();

        ActionCommandService service = new ActionCommandService(
                actionInstanceRepository,
                actionOutboxRepository,
                actionStepInstanceRepository,
                new ActionCommandValidator(),
                new ActionAuditService(auditLogRepository),
                Optional.of(producer),
                new NoOpCompensationService(),
                new ActionObservabilityService(Optional.empty(), Optional.of(metricsRecorder), Clock.fixed(Instant.parse("2026-06-26T12:00:00Z"), ZoneOffset.UTC))
        );

        service.retry("act-1", "anonymous");

        assertThat(producer.published()).hasSize(1);
        assertThat(auditLogRepository.findByActionInstanceId("act-1")).hasSize(1);
        assertThat(auditLogRepository.findByActionInstanceId("act-1").get(0).operationType()).isEqualTo("RETRY");
        assertThat(metricsRecorder.counters)
                .containsEntry("action.guard.governance.command|{actionName=unknown, command=RETRY, result=SUCCESS, stepType=unknown}", 1L);
    }

    @Test
    void shouldAuditFailedRetryWhenProducerIsUnavailable() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        ActionAuditLogRepository auditLogRepository = InMemoryAuditLogRepository.create();
        CapturingMetricsRecorder metricsRecorder = new CapturingMetricsRecorder();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.FAILED, 0, 1, Map.of(),
                "DOWNSTREAM_ERROR", "sms provider failed", 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));
        actionOutboxRepository.save(new ActionOutbox(
                "outbox-1", "act-1", "ACTION_EXECUTE", ActionOutboxStatus.NEW, Instant.parse("2026-06-26T12:00:00Z"),
                0, 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));

        ActionCommandService service = new ActionCommandService(
                actionInstanceRepository,
                actionOutboxRepository,
                actionStepInstanceRepository,
                new ActionCommandValidator(),
                new ActionAuditService(auditLogRepository),
                Optional.empty(),
                new NoOpCompensationService(),
                new ActionObservabilityService(Optional.empty(), Optional.of(metricsRecorder), Clock.fixed(Instant.parse("2026-06-26T12:00:00Z"), ZoneOffset.UTC))
        );

        assertThatThrownBy(() -> service.retry("act-1", "anonymous"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ActionExecutionMessageProducer is not available");
        assertThat(auditLogRepository.findByActionInstanceId("act-1")).hasSize(1);
        assertThat(auditLogRepository.findByActionInstanceId("act-1").get(0).resultStatus()).isEqualTo("FAILED");
        assertThat(metricsRecorder.counters)
                .containsEntry("action.guard.governance.command|{actionName=unknown, command=RETRY, result=FAILED, stepType=unknown}", 1L);
    }

    @Test
    void shouldRejectRetryWhenActionStatusIsDeadAndStillAuditFailure() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        ActionAuditLogRepository auditLogRepository = InMemoryAuditLogRepository.create();
        CapturingMetricsRecorder metricsRecorder = new CapturingMetricsRecorder();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.DEAD, 0, 1, Map.of(),
                "DEAD_LETTER", "message dead-lettered", 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));
        actionOutboxRepository.save(new ActionOutbox(
                "outbox-1", "act-1", "ACTION_EXECUTE", ActionOutboxStatus.DEAD, Instant.parse("2026-06-26T12:00:00Z"),
                2, 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));

        ActionCommandService service = new ActionCommandService(
                actionInstanceRepository,
                actionOutboxRepository,
                actionStepInstanceRepository,
                new ActionCommandValidator(),
                new ActionAuditService(auditLogRepository),
                Optional.of(new CapturingProducer()),
                new NoOpCompensationService(),
                new ActionObservabilityService(Optional.empty(), Optional.of(metricsRecorder), Clock.fixed(Instant.parse("2026-06-26T12:00:00Z"), ZoneOffset.UTC))
        );

        assertThatThrownBy(() -> service.retry("act-1", "anonymous"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Retry is not allowed");
        assertThat(auditLogRepository.findByActionInstanceId("act-1")).hasSize(1);
        assertThat(auditLogRepository.findByActionInstanceId("act-1").get(0).resultStatus()).isEqualTo("FAILED");
        assertThat(metricsRecorder.counters)
                .containsEntry("action.guard.governance.command|{actionName=unknown, command=RETRY, result=FAILED, stepType=unknown}", 1L);
    }

    @Test
    void shouldPublishRetryAfterTransactionCommit() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        ActionAuditLogRepository auditLogRepository = InMemoryAuditLogRepository.create();
        CapturingProducer producer = new CapturingProducer();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.FAILED, 0, 1, Map.of(),
                "DOWNSTREAM_ERROR", "sms provider failed", 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));
        actionOutboxRepository.save(new ActionOutbox(
                "outbox-1", "act-1", "ACTION_EXECUTE", ActionOutboxStatus.NEW, Instant.parse("2026-06-26T12:00:00Z"),
                0, 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));

        ActionCommandService service = new ActionCommandService(
                actionInstanceRepository,
                actionOutboxRepository,
                actionStepInstanceRepository,
                new ActionCommandValidator(),
                new ActionAuditService(auditLogRepository),
                Optional.of(producer),
                new NoOpCompensationService()
        );

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.retry("act-1", "anonymous");

            assertThat(producer.published()).isEmpty();

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

            assertThat(producer.published()).hasSize(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldCancelDispatchingActionAndMoveToIgnored() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        ActionAuditLogRepository auditLogRepository = InMemoryAuditLogRepository.create();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.DISPATCHING, 0, 1, Map.of(),
                null, null, 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));

        ActionCommandService service = new ActionCommandService(
                actionInstanceRepository,
                actionOutboxRepository,
                actionStepInstanceRepository,
                new ActionCommandValidator(),
                new ActionAuditService(auditLogRepository),
                Optional.empty(),
                new NoOpCompensationService()
        );

        service.cancel("act-1", "anonymous");

        assertThat(actionInstanceRepository.findById("act-1").orElseThrow().status()).isEqualTo(ActionStatus.IGNORED);
        assertThat(auditLogRepository.findByActionInstanceId("act-1")).hasSize(1);
        assertThat(auditLogRepository.findByActionInstanceId("act-1").get(0).operationType()).isEqualTo("CANCEL");
    }

    @Test
    void shouldSkipCurrentStepAndAdvanceAction() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        ActionAuditLogRepository auditLogRepository = InMemoryAuditLogRepository.create();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.DISPATCHING, 0, 1, Map.of(),
                null, null, 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));
        actionStepInstanceRepository.save(new ActionStepInstance(
                "step-1", "act-1", 0, "send-user-sms", "SMS", "notify.user", ActionStepStatus.PENDING, 0, Map.of(),
                null, null, 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));

        ActionCommandService service = new ActionCommandService(
                actionInstanceRepository,
                actionOutboxRepository,
                actionStepInstanceRepository,
                new ActionCommandValidator(),
                new ActionAuditService(auditLogRepository),
                Optional.empty(),
                new NoOpCompensationService()
        );

        service.skip("act-1", "anonymous");

        assertThat(actionInstanceRepository.findById("act-1").orElseThrow().status()).isEqualTo(ActionStatus.SUCCESS);
        assertThat(actionStepInstanceRepository.findByActionInstanceId("act-1").get(0).status()).isEqualTo(ActionStepStatus.SUCCESS);
        assertThat(auditLogRepository.findByActionInstanceId("act-1")).hasSize(1);
        assertThat(auditLogRepository.findByActionInstanceId("act-1").get(0).operationType()).isEqualTo("SKIP");
    }

    @Test
    void shouldScheduleNextStepWhenSkipAdvancesMultiStepAction() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        ActionAuditLogRepository auditLogRepository = InMemoryAuditLogRepository.create();
        CapturingProducer producer = new CapturingProducer();
        Instant now = Instant.parse("2026-06-26T12:00:00Z");
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.DISPATCHING, 0, 2, Map.of(),
                null, null, 0, now, now
        ));
        actionStepInstanceRepository.saveAll(List.of(
                new ActionStepInstance("step-1", "act-1", 0, "send-cancel-event", "MQ_MESSAGE", "order.cancel.exchange", ActionStepStatus.PENDING, 0, Map.of(), null, null, 0, now, now),
                new ActionStepInstance("step-2", "act-1", 1, "send-user-sms", "SMS", "notify.user", ActionStepStatus.PENDING, 0, Map.of(), null, null, 0, now, now)
        ));
        actionOutboxRepository.save(new ActionOutbox(
                "outbox-1", "act-1", "ACTION_EXECUTE", ActionOutboxStatus.DONE, now,
                0, 0, now, now
        ));

        ActionCommandService service = new ActionCommandService(
                actionInstanceRepository,
                actionOutboxRepository,
                actionStepInstanceRepository,
                new ActionCommandValidator(),
                new ActionAuditService(auditLogRepository),
                Optional.of(producer),
                new NoOpCompensationService()
        );

        service.skip("act-1", "anonymous");

        ActionInstance updated = actionInstanceRepository.findById("act-1").orElseThrow();
        ActionOutbox updatedOutbox = actionOutboxRepository.findByActionInstanceId("act-1").orElseThrow();
        assertThat(updated.status()).isEqualTo(ActionStatus.DISPATCHING);
        assertThat(updated.currentStepIndex()).isEqualTo(1);
        assertThat(actionStepInstanceRepository.findByActionInstanceId("act-1").get(0).status()).isEqualTo(ActionStepStatus.SUCCESS);
        assertThat(updatedOutbox.status()).isEqualTo(ActionOutboxStatus.NEW);
        assertThat(producer.published()).hasSize(1);
        assertThat(producer.published().get(0).id()).isEqualTo("outbox-1");
        assertThat(auditLogRepository.findByActionInstanceId("act-1")).hasSize(1);
        assertThat(auditLogRepository.findByActionInstanceId("act-1").get(0).resultStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldRejectCompensateWhenCapabilityIsNotEnabledButStillAudit() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        ActionAuditLogRepository auditLogRepository = InMemoryAuditLogRepository.create();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.FAILED, 0, 1, Map.of(),
                "DOWNSTREAM_ERROR", "sms provider failed", 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));

        ActionCommandService service = new ActionCommandService(
                actionInstanceRepository,
                actionOutboxRepository,
                actionStepInstanceRepository,
                new ActionCommandValidator(),
                new ActionAuditService(auditLogRepository),
                Optional.empty(),
                new FailingCompensationService()
        );

        assertThatThrownBy(() -> service.compensate("act-1", "anonymous"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("compensation capability is not enabled");
        assertThat(auditLogRepository.findByActionInstanceId("act-1")).hasSize(1);
        assertThat(auditLogRepository.findByActionInstanceId("act-1").get(0).operationType()).isEqualTo("COMPENSATE");
        assertThat(auditLogRepository.findByActionInstanceId("act-1").get(0).resultStatus()).isEqualTo("FAILED");
    }

    @Test
    void shouldDelegateCompensateToRuntimeService() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        ActionAuditLogRepository auditLogRepository = InMemoryAuditLogRepository.create();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.FAILED, 0, 1, Map.of(),
                "DOWNSTREAM_ERROR", "sms provider failed", 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));

        CapturingCompensationService compensationService = new CapturingCompensationService();
        ActionCommandService service = new ActionCommandService(
                actionInstanceRepository,
                actionOutboxRepository,
                actionStepInstanceRepository,
                new ActionCommandValidator(),
                new ActionAuditService(auditLogRepository),
                Optional.empty(),
                compensationService
        );

        service.compensate("act-1", "anonymous");

        assertThat(compensationService.compensatedActionIds()).containsExactly("act-1");
        assertThat(auditLogRepository.findByActionInstanceId("act-1")).hasSize(1);
        assertThat(auditLogRepository.findByActionInstanceId("act-1").get(0).operationType()).isEqualTo("COMPENSATE");
        assertThat(auditLogRepository.findByActionInstanceId("act-1").get(0).resultStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldAllowCompensateAgainWhenActionIsDead() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        ActionAuditLogRepository auditLogRepository = InMemoryAuditLogRepository.create();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.DEAD, 0, 1, Map.of(),
                "COMPENSATION_FAILED", "first compensation failed", 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:01:00Z")
        ));

        CapturingCompensationService compensationService = new CapturingCompensationService();
        ActionCommandService service = new ActionCommandService(
                actionInstanceRepository,
                actionOutboxRepository,
                actionStepInstanceRepository,
                new ActionCommandValidator(),
                new ActionAuditService(auditLogRepository),
                Optional.empty(),
                compensationService
        );

        service.compensate("act-1", "anonymous");

        assertThat(compensationService.compensatedActionIds()).containsExactly("act-1");
        assertThat(auditLogRepository.findByActionInstanceId("act-1")).hasSize(1);
        assertThat(auditLogRepository.findByActionInstanceId("act-1").get(0).resultStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldTreatRepeatedCancelAsIdempotentSuccess() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        ActionAuditLogRepository auditLogRepository = InMemoryAuditLogRepository.create();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.IGNORED, 0, 1, Map.of(),
                null, null, 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:01:00Z")
        ));

        ActionCommandService service = new ActionCommandService(
                actionInstanceRepository,
                actionOutboxRepository,
                actionStepInstanceRepository,
                new ActionCommandValidator(),
                new ActionAuditService(auditLogRepository),
                Optional.empty(),
                new NoOpCompensationService()
        );

        service.cancel("act-1", "anonymous");

        assertThat(auditLogRepository.findByActionInstanceId("act-1")).hasSize(1);
        assertThat(auditLogRepository.findByActionInstanceId("act-1").get(0).resultStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldTreatRepeatedCompensateAsIdempotentSuccess() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        ActionAuditLogRepository auditLogRepository = InMemoryAuditLogRepository.create();
        CapturingCompensationService compensationService = new CapturingCompensationService();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.COMPENSATED, 0, 1, Map.of(),
                null, null, 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:01:00Z")
        ));

        ActionCommandService service = new ActionCommandService(
                actionInstanceRepository,
                actionOutboxRepository,
                actionStepInstanceRepository,
                new ActionCommandValidator(),
                new ActionAuditService(auditLogRepository),
                Optional.empty(),
                compensationService
        );

        service.compensate("act-1", "anonymous");

        assertThat(compensationService.compensatedActionIds()).isEmpty();
        assertThat(auditLogRepository.findByActionInstanceId("act-1")).hasSize(1);
        assertThat(auditLogRepository.findByActionInstanceId("act-1").get(0).resultStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldTreatRetryingActionWithScheduledOutboxAsIdempotentRetry() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        ActionAuditLogRepository auditLogRepository = InMemoryAuditLogRepository.create();
        CapturingProducer producer = new CapturingProducer();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.RETRYING, 0, 1, Map.of(),
                "DOWNSTREAM_ERROR", "sms provider failed", 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:01:00Z")
        ));
        actionOutboxRepository.save(new ActionOutbox(
                "outbox-1", "act-1", "ACTION_EXECUTE", ActionOutboxStatus.NEW, Instant.parse("2026-06-26T12:02:00Z"),
                1, 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:01:00Z")
        ));

        ActionCommandService service = new ActionCommandService(
                actionInstanceRepository,
                actionOutboxRepository,
                actionStepInstanceRepository,
                new ActionCommandValidator(),
                new ActionAuditService(auditLogRepository),
                Optional.of(producer),
                new NoOpCompensationService()
        );

        service.retry("act-1", "anonymous");

        assertThat(producer.published()).isEmpty();
        assertThat(auditLogRepository.findByActionInstanceId("act-1")).hasSize(1);
        assertThat(auditLogRepository.findByActionInstanceId("act-1").get(0).resultStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldIgnoreRedeliveredMessageAfterManualCancelWinsConflict() throws Exception {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        ActionAuditLogRepository auditLogRepository = InMemoryAuditLogRepository.create();
        Instant now = Instant.parse("2026-06-26T12:00:00Z");
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.DISPATCHING, 0, 1, Map.of(),
                null, null, 0, now, now
        ));
        actionStepInstanceRepository.save(new ActionStepInstance(
                "step-1", "act-1", 0, "send-user-sms", "SMS", "notify.user", ActionStepStatus.PENDING, 0, Map.of(),
                null, null, 0, now, now
        ));
        actionOutboxRepository.save(new ActionOutbox(
                "outbox-1", "act-1", "ACTION_EXECUTE", ActionOutboxStatus.NEW, now,
                0, 0, now, now
        ));

        BlockingSuccessHandler handler = new BlockingSuccessHandler("SMS");
        ActionExecutionCallback callback = new DefaultActionExecutionCallback(
                actionInstanceRepository,
                actionStepInstanceRepository,
                definitionRegistry(),
                new StepHandlerRegistry(List.of(handler)),
                new FixedAttemptActionRetryPolicy(0),
                actionOutboxRepository,
                Optional.empty(),
                Clock.fixed(now.plusSeconds(30), java.time.ZoneOffset.UTC)
        );
        ActionCommandService service = new ActionCommandService(
                actionInstanceRepository,
                actionOutboxRepository,
                actionStepInstanceRepository,
                new ActionCommandValidator(),
                new ActionAuditService(auditLogRepository),
                Optional.empty(),
                new NoOpCompensationService()
        );
        ActionExecutionMessage message = new ActionExecutionMessage(
                "ACTION_EXECUTE:outbox-1",
                "ACTION_EXECUTE:act-1",
                "outbox-1",
                "act-1",
                "ACTION_EXECUTE",
                now
        );
        AtomicReference<Throwable> runtimeFailure = new AtomicReference<>();
        Thread runtimeThread = new Thread(() -> {
            try {
                callback.execute(message);
            } catch (Throwable ex) {
                runtimeFailure.set(ex);
            }
        });

        runtimeThread.start();
        assertThat(handler.started.await(2, TimeUnit.SECONDS)).isTrue();

        service.cancel("act-1", "anonymous");
        handler.release.countDown();
        runtimeThread.join(2000);

        assertThat(runtimeFailure.get()).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(actionInstanceRepository.findById("act-1").orElseThrow().status()).isEqualTo(ActionStatus.IGNORED);
        assertThat(handler.invocationCount()).isEqualTo(1);

        callback.execute(message);

        assertThat(handler.invocationCount()).isEqualTo(1);
        assertThat(auditLogRepository.findByActionInstanceId("act-1")).hasSize(1);
        assertThat(auditLogRepository.findByActionInstanceId("act-1").get(0).operationType()).isEqualTo("CANCEL");
    }

    private static ActionDefinitionRegistry definitionRegistry() {
        return new InMemoryActionDefinitionRegistry(
                List.of(new ActionDefinition(
                        "order-cancel-flow",
                        "demo",
                        false,
                        List.of(new ActionStepDefinition("send-user-sms", "SMS", "notify.user", null, null, null))
                )),
                new ActionDefinitionValidator()
        );
    }

    @Test
    void shouldAuditFailedCancelWhenVersionConflictOccurs() {
        ConflictActionInstanceRepository actionInstanceRepository = new ConflictActionInstanceRepository();
        InMemoryActionOutboxRepository actionOutboxRepository = new InMemoryActionOutboxRepository();
        InMemoryActionStepInstanceRepository actionStepInstanceRepository = new InMemoryActionStepInstanceRepository();
        ActionAuditLogRepository auditLogRepository = InMemoryAuditLogRepository.create();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.DISPATCHING, 0, 1, Map.of(),
                null, null, 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));
        actionInstanceRepository.forceNextConflict();

        ActionCommandService service = new ActionCommandService(
                actionInstanceRepository,
                actionOutboxRepository,
                actionStepInstanceRepository,
                new ActionCommandValidator(),
                new ActionAuditService(auditLogRepository),
                Optional.empty(),
                new NoOpCompensationService()
        );

        assertThatThrownBy(() -> service.cancel("act-1", "anonymous"))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(auditLogRepository.findByActionInstanceId("act-1")).hasSize(1);
        assertThat(auditLogRepository.findByActionInstanceId("act-1").get(0).resultStatus()).isEqualTo("FAILED");
    }

    private static final class CapturingProducer implements ActionExecutionMessageProducer {
        private final List<ActionOutbox> published = new ArrayList<>();

        @Override
        public void publish(ActionOutbox outbox) {
            published.add(outbox);
        }

        private List<ActionOutbox> published() {
            return List.copyOf(published);
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

    private static final class CapturingCompensationService implements ActionCompensationExecutor {
        private final List<String> compensatedActionIds = new ArrayList<>();

        @Override
        public void compensate(String actionInstanceId) {
            compensatedActionIds.add(actionInstanceId);
        }

        private List<String> compensatedActionIds() {
            return List.copyOf(compensatedActionIds);
        }
    }

    private static final class FailingCompensationService implements ActionCompensationExecutor {

        @Override
        public void compensate(String actionInstanceId) {
            throw new IllegalStateException("compensation capability is not enabled");
        }
    }

    private static final class NoOpCompensationService implements ActionCompensationExecutor {

        @Override
        public void compensate(String actionInstanceId) {
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
                throw new OptimisticLockingFailureException("forced conflict");
            }
            return super.save(instance);
        }
    }

    private static final class BlockingSuccessHandler implements ActionStepHandler {
        private final String stepType;
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private volatile int invocationCount;

        private BlockingSuccessHandler(String stepType) {
            this.stepType = stepType;
        }

        @Override
        public String stepType() {
            return stepType;
        }

        @Override
        public StepExecutionResult execute(ActionStepContext context) {
            invocationCount++;
            started.countDown();
            try {
                if (!release.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting for release");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", ex);
            }
            return StepExecutionResult.succeeded();
        }

        private int invocationCount() {
            return invocationCount;
        }
    }
}
