package io.github.actionguard.core.runtime;

import io.github.actionguard.api.definition.ActionDefinition;
import io.github.actionguard.api.runtime.ActionCompensationContext;
import io.github.actionguard.api.runtime.ActionCompensationResult;
import io.github.actionguard.api.spi.ActionCompensator;
import io.github.actionguard.core.model.ActionCompensationLog;
import io.github.actionguard.core.model.ActionGovernancePolicy;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.model.ActionStepStatus;
import io.github.actionguard.core.repository.ActionCompensationLogRepository;
import io.github.actionguard.core.repository.ActionGovernancePolicyRepository;
import io.github.actionguard.core.repository.InMemoryActionInstanceRepository;
import io.github.actionguard.core.repository.InMemoryActionStepInstanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionCompensationServiceTest {

    @Test
    void shouldRejectCompensationWhenYamlAndDbDisableIt() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionStepInstanceRepository stepRepository = new InMemoryActionStepInstanceRepository();
        InMemoryActionGovernancePolicyRepository policyRepository = new InMemoryActionGovernancePolicyRepository();
        InMemoryActionCompensationLogRepository compensationLogRepository = new InMemoryActionCompensationLogRepository();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.FAILED, 1, 2, Map.of(),
                "DOWNSTREAM_ERROR", "failed", 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));
        stepRepository.saveAll(successSteps());

        ActionCompensationService service = new ActionCompensationService(
                actionInstanceRepository,
                stepRepository,
                new FixedDefinitionRegistry(false),
                policyRepository,
                compensationLogRepository,
                new ActionCompensatorRegistry(List.of()),
                Clock.fixed(Instant.parse("2026-06-26T12:01:00Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.compensate("act-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("compensation is disabled");
    }

    @Test
    void shouldUseDatabasePolicyToOverrideYamlDefault() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionStepInstanceRepository stepRepository = new InMemoryActionStepInstanceRepository();
        InMemoryActionGovernancePolicyRepository policyRepository = new InMemoryActionGovernancePolicyRepository();
        InMemoryActionCompensationLogRepository compensationLogRepository = new InMemoryActionCompensationLogRepository();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.FAILED, 1, 2, Map.of(),
                "DOWNSTREAM_ERROR", "failed", 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));
        stepRepository.saveAll(successSteps());
        policyRepository.save(new ActionGovernancePolicy(
                "policy-1",
                "order-cancel-flow",
                Boolean.TRUE,
                null,
                null,
                Instant.parse("2026-06-26T12:00:00Z")
        ));

        CapturingCompensator first = new CapturingCompensator("MQ_MESSAGE");
        CapturingCompensator second = new CapturingCompensator("SMS");
        ActionCompensationService service = new ActionCompensationService(
                actionInstanceRepository,
                stepRepository,
                new FixedDefinitionRegistry(false),
                policyRepository,
                compensationLogRepository,
                new ActionCompensatorRegistry(List.of(first, second)),
                Clock.fixed(Instant.parse("2026-06-26T12:01:00Z"), ZoneOffset.UTC)
        );

        service.compensate("act-1");

        assertThat(first.invocations()).hasSize(1);
        assertThat(second.invocations()).hasSize(1);
        assertThat(compensationLogRepository.findByActionInstanceId("act-1"))
                .extracting(ActionCompensationLog::compensationStatus)
                .containsExactly("SUCCESS", "SUCCESS");
    }

    @Test
    void shouldCompensateSuccessfulStepsInReverseOrder() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionStepInstanceRepository stepRepository = new InMemoryActionStepInstanceRepository();
        InMemoryActionGovernancePolicyRepository policyRepository = new InMemoryActionGovernancePolicyRepository();
        InMemoryActionCompensationLogRepository compensationLogRepository = new InMemoryActionCompensationLogRepository();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.FAILED, 1, 2, Map.of(),
                "DOWNSTREAM_ERROR", "failed", 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));
        stepRepository.saveAll(successSteps());
        policyRepository.save(new ActionGovernancePolicy(
                "policy-1", "order-cancel-flow", Boolean.TRUE, null, null, Instant.parse("2026-06-26T12:00:00Z")
        ));

        List<String> stepNames = new ArrayList<>();
        RecordingCompensator mqCompensator = new RecordingCompensator("MQ_MESSAGE", stepNames);
        RecordingCompensator smsCompensator = new RecordingCompensator("SMS", stepNames);
        ActionCompensationService service = new ActionCompensationService(
                actionInstanceRepository,
                stepRepository,
                new FixedDefinitionRegistry(false),
                policyRepository,
                compensationLogRepository,
                new ActionCompensatorRegistry(List.of(mqCompensator, smsCompensator)),
                Clock.fixed(Instant.parse("2026-06-26T12:01:00Z"), ZoneOffset.UTC)
        );

        service.compensate("act-1");

        assertThat(stepNames).containsExactly("send-user-sms", "send-cancel-event");
        assertThat(compensationLogRepository.findByActionInstanceId("act-1"))
                .extracting(ActionCompensationLog::compensationBatchId)
                .containsOnly("batch-act-1");
    }

    @Test
    void shouldSkipSuccessfulStepWithoutCompensator() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionStepInstanceRepository stepRepository = new InMemoryActionStepInstanceRepository();
        InMemoryActionGovernancePolicyRepository policyRepository = new InMemoryActionGovernancePolicyRepository();
        InMemoryActionCompensationLogRepository compensationLogRepository = new InMemoryActionCompensationLogRepository();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.FAILED, 1, 2, Map.of(),
                "DOWNSTREAM_ERROR", "failed", 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));
        stepRepository.saveAll(successSteps());
        policyRepository.save(new ActionGovernancePolicy(
                "policy-1", "order-cancel-flow", Boolean.TRUE, null, null, Instant.parse("2026-06-26T12:00:00Z")
        ));

        CapturingCompensator smsOnly = new CapturingCompensator("SMS");
        ActionCompensationService service = new ActionCompensationService(
                actionInstanceRepository,
                stepRepository,
                new FixedDefinitionRegistry(false),
                policyRepository,
                compensationLogRepository,
                new ActionCompensatorRegistry(List.of(smsOnly)),
                Clock.fixed(Instant.parse("2026-06-26T12:01:00Z"), ZoneOffset.UTC)
        );

        service.compensate("act-1");

        assertThat(smsOnly.invocations()).hasSize(1);
        assertThat(actionInstanceRepository.findById("act-1").orElseThrow().status()).isEqualTo(ActionStatus.COMPENSATED);
        assertThat(compensationLogRepository.findByActionInstanceId("act-1"))
                .extracting(ActionCompensationLog::compensationStatus)
                .containsExactly("SUCCESS", "SKIPPED");
    }

    @Test
    void shouldMoveActionToDeadWhenCompensatorFails() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionStepInstanceRepository stepRepository = new InMemoryActionStepInstanceRepository();
        InMemoryActionGovernancePolicyRepository policyRepository = new InMemoryActionGovernancePolicyRepository();
        InMemoryActionCompensationLogRepository compensationLogRepository = new InMemoryActionCompensationLogRepository();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.FAILED, 1, 2, Map.of(),
                "DOWNSTREAM_ERROR", "failed", 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));
        stepRepository.saveAll(successSteps());
        policyRepository.save(new ActionGovernancePolicy(
                "policy-1", "order-cancel-flow", Boolean.TRUE, null, null, Instant.parse("2026-06-26T12:00:00Z")
        ));

        ActionCompensationService service = new ActionCompensationService(
                actionInstanceRepository,
                stepRepository,
                new FixedDefinitionRegistry(false),
                policyRepository,
                compensationLogRepository,
                new ActionCompensatorRegistry(List.of(new FailingCompensator("SMS"))),
                Clock.fixed(Instant.parse("2026-06-26T12:01:00Z"), ZoneOffset.UTC)
        );

        service.compensate("act-1");

        ActionInstance updated = actionInstanceRepository.findById("act-1").orElseThrow();
        assertThat(updated.status()).isEqualTo(ActionStatus.DEAD);
        assertThat(updated.lastErrorMessage()).contains("compensation failed");
        assertThat(compensationLogRepository.findByActionInstanceId("act-1"))
                .extracting(ActionCompensationLog::compensationStatus)
                .containsExactly("FAILED");
    }

    @Test
    void shouldMoveActionToCompensatedWhenAllCompensationsSucceed() {
        InMemoryActionInstanceRepository actionInstanceRepository = new InMemoryActionInstanceRepository();
        InMemoryActionStepInstanceRepository stepRepository = new InMemoryActionStepInstanceRepository();
        InMemoryActionGovernancePolicyRepository policyRepository = new InMemoryActionGovernancePolicyRepository();
        InMemoryActionCompensationLogRepository compensationLogRepository = new InMemoryActionCompensationLogRepository();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.FAILED, 1, 2, Map.of(),
                "DOWNSTREAM_ERROR", "failed", 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));
        stepRepository.saveAll(successSteps());
        policyRepository.save(new ActionGovernancePolicy(
                "policy-1", "order-cancel-flow", Boolean.TRUE, null, null, Instant.parse("2026-06-26T12:00:00Z")
        ));

        ActionCompensationService service = new ActionCompensationService(
                actionInstanceRepository,
                stepRepository,
                new FixedDefinitionRegistry(false),
                policyRepository,
                compensationLogRepository,
                new ActionCompensatorRegistry(List.of(new CapturingCompensator("MQ_MESSAGE"), new CapturingCompensator("SMS"))),
                Clock.fixed(Instant.parse("2026-06-26T12:01:00Z"), ZoneOffset.UTC)
        );

        service.compensate("act-1");

        assertThat(actionInstanceRepository.findById("act-1").orElseThrow().status()).isEqualTo(ActionStatus.COMPENSATED);
        assertThat(compensationLogRepository.findByActionInstanceId("act-1"))
                .extracting(ActionCompensationLog::compensationStatus)
                .containsExactly("SUCCESS", "SUCCESS");
    }

    @Test
    void shouldStopCompensationWhenActionVersionConflictsBeforeCompensating() {
        ConflictActionInstanceRepository actionInstanceRepository = new ConflictActionInstanceRepository();
        InMemoryActionStepInstanceRepository stepRepository = new InMemoryActionStepInstanceRepository();
        InMemoryActionGovernancePolicyRepository policyRepository = new InMemoryActionGovernancePolicyRepository();
        InMemoryActionCompensationLogRepository compensationLogRepository = new InMemoryActionCompensationLogRepository();
        actionInstanceRepository.save(new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.FAILED, 1, 2, Map.of(),
                "DOWNSTREAM_ERROR", "failed", 0, Instant.parse("2026-06-26T12:00:00Z"), Instant.parse("2026-06-26T12:00:00Z")
        ));
        stepRepository.saveAll(successSteps());
        policyRepository.save(new ActionGovernancePolicy(
                "policy-1", "order-cancel-flow", Boolean.TRUE, null, null, Instant.parse("2026-06-26T12:00:00Z")
        ));
        actionInstanceRepository.forceNextConflict();

        ActionCompensationService service = new ActionCompensationService(
                actionInstanceRepository,
                stepRepository,
                new FixedDefinitionRegistry(false),
                policyRepository,
                compensationLogRepository,
                new ActionCompensatorRegistry(List.of(new CapturingCompensator("MQ_MESSAGE"), new CapturingCompensator("SMS"))),
                Clock.fixed(Instant.parse("2026-06-26T12:01:00Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.compensate("act-1"))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(compensationLogRepository.findByActionInstanceId("act-1")).isEmpty();
    }

    private List<ActionStepInstance> successSteps() {
        Instant now = Instant.parse("2026-06-26T12:00:00Z");
        return List.of(
                new ActionStepInstance("step-1", "act-1", 0, "send-cancel-event", "MQ_MESSAGE", "order.cancel.exchange", ActionStepStatus.SUCCESS, 1, Map.of("orderId", "1"), null, null, 0, now, now),
                new ActionStepInstance("step-2", "act-1", 1, "send-user-sms", "SMS", "notify.user", ActionStepStatus.SUCCESS, 1, Map.of("phone", "13800000000"), null, null, 0, now, now)
        );
    }

    private static final class FixedDefinitionRegistry implements ActionDefinitionRegistry {
        private final boolean compensationEnabled;

        private FixedDefinitionRegistry(boolean compensationEnabled) {
            this.compensationEnabled = compensationEnabled;
        }

        @Override
        public Optional<ActionDefinition> find(String name) {
            return Optional.of(new ActionDefinition(name, "demo", compensationEnabled, List.of()));
        }

        @Override
        public ActionDefinition getRequired(String name) {
            return find(name).orElseThrow();
        }

        @Override
        public List<ActionDefinition> getAll() {
            return List.of();
        }
    }

    private static final class InMemoryActionGovernancePolicyRepository implements ActionGovernancePolicyRepository {
        private final List<ActionGovernancePolicy> storage = new ArrayList<>();

        @Override
        public Optional<ActionGovernancePolicy> findByActionName(String actionName) {
            return storage.stream().filter(policy -> policy.actionName().equals(actionName)).findFirst();
        }

        @Override
        public ActionGovernancePolicy save(ActionGovernancePolicy policy) {
            storage.removeIf(existing -> existing.actionName().equals(policy.actionName()));
            storage.add(policy);
            return policy;
        }
    }

    private static final class InMemoryActionCompensationLogRepository implements ActionCompensationLogRepository {
        private final List<ActionCompensationLog> storage = new ArrayList<>();

        @Override
        public ActionCompensationLog save(ActionCompensationLog log) {
            storage.add(log);
            return log;
        }

        @Override
        public List<ActionCompensationLog> findByActionInstanceId(String actionInstanceId) {
            return storage.stream().filter(log -> log.actionInstanceId().equals(actionInstanceId)).toList();
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

    private static final class CapturingCompensator implements ActionCompensator {
        private final String stepType;
        private final List<ActionCompensationContext> invocations = new ArrayList<>();

        private CapturingCompensator(String stepType) {
            this.stepType = stepType;
        }

        @Override
        public String stepType() {
            return stepType;
        }

        @Override
        public ActionCompensationResult compensate(ActionCompensationContext context) {
            invocations.add(context);
            return ActionCompensationResult.success("ok");
        }

        private List<ActionCompensationContext> invocations() {
            return List.copyOf(invocations);
        }
    }

    private static final class RecordingCompensator implements ActionCompensator {
        private final String stepType;
        private final List<String> stepNames;

        private RecordingCompensator(String stepType, List<String> stepNames) {
            this.stepType = stepType;
            this.stepNames = stepNames;
        }

        @Override
        public String stepType() {
            return stepType;
        }

        @Override
        public ActionCompensationResult compensate(ActionCompensationContext context) {
            stepNames.add(context.stepName());
            return ActionCompensationResult.success("ok");
        }
    }

    private static final class FailingCompensator implements ActionCompensator {
        private final String stepType;

        private FailingCompensator(String stepType) {
            this.stepType = stepType;
        }

        @Override
        public String stepType() {
            return stepType;
        }

        @Override
        public ActionCompensationResult compensate(ActionCompensationContext context) {
            return ActionCompensationResult.failure("compensation failed");
        }
    }
}
