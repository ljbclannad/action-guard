package io.github.actionguard.ops.api.service;

import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.model.ActionStepStatus;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.repository.InMemoryActionInstanceRepository;
import io.github.actionguard.core.repository.InMemoryActionOutboxRepository;
import io.github.actionguard.core.repository.InMemoryActionStepInstanceRepository;
import io.github.actionguard.core.runtime.ActionCompensationExecutor;
import io.github.actionguard.ops.api.support.ActionCommandValidator;
import io.github.actionguard.core.runtime.ActionExecutionMessageProducer;
import io.github.actionguard.ops.api.repository.ActionAuditLogRepository;
import io.github.actionguard.ops.api.repository.jdbc.InMemoryAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

        assertThat(producer.published()).hasSize(1);
        assertThat(auditLogRepository.findByActionInstanceId("act-1")).hasSize(1);
        assertThat(auditLogRepository.findByActionInstanceId("act-1").get(0).operationType()).isEqualTo("RETRY");
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
}
