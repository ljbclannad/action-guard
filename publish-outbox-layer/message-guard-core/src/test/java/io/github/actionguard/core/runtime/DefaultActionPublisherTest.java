package io.github.actionguard.core.runtime;

import io.github.actionguard.api.ActionRequest;
import io.github.actionguard.api.ActionStepRequest;
import io.github.actionguard.api.definition.ActionDefinition;
import io.github.actionguard.api.definition.ActionStepDefinition;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.model.ActionStepStatus;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.repository.ActionStepInstanceRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultActionPublisherTest {

    @Test
    void shouldCreateActionInstanceStepInstancesAndOutboxFromDefinition() {
        ActionDefinition definition = new ActionDefinition(
                "order-cancel-flow",
                "demo",
                false,
                List.of(
                        new ActionStepDefinition("send-cancel-event", "MQ_MESSAGE", "order.cancel.exchange", null, null, null),
                        new ActionStepDefinition("send-user-sms", "SMS", "notify.user", null, null, null)
                )
        );
        ActionDefinitionRegistry definitionRegistry = new InMemoryActionDefinitionRegistry(
                List.of(definition),
                new ActionDefinitionValidator()
        );
        CapturingActionInstanceRepository actionInstanceRepository = new CapturingActionInstanceRepository();
        CapturingActionStepInstanceRepository actionStepInstanceRepository = new CapturingActionStepInstanceRepository();
        CapturingActionOutboxRepository actionOutboxRepository = new CapturingActionOutboxRepository();
        DefaultActionPublisher publisher = new DefaultActionPublisher(
                definitionRegistry,
                actionInstanceRepository,
                actionStepInstanceRepository,
                actionOutboxRepository,
                Clock.fixed(Instant.parse("2026-06-26T07:30:00Z"), ZoneOffset.UTC)
        );

        publisher.publish(new ActionRequest(
                "order-cancel-flow",
                "order:1",
                Map.of("operator", "demo"),
                List.of(
                        new ActionStepRequest("ignored-name", "ignored-type", "ignored-target", Map.of("orderId", "1")),
                        new ActionStepRequest("ignored-name-2", "ignored-type-2", "ignored-target-2", Map.of("template", "order-cancel"))
                )
        ));

        assertThat(actionInstanceRepository.saved).isNotNull();
        assertThat(actionInstanceRepository.saved.actionName()).isEqualTo("order-cancel-flow");
        assertThat(actionInstanceRepository.saved.bizKey()).isEqualTo("order:1");
        assertThat(actionInstanceRepository.saved.status()).isEqualTo(ActionStatus.NEW);
        assertThat(actionInstanceRepository.saved.totalStepCount()).isEqualTo(2);
        assertThat(actionInstanceRepository.saved.attributes()).containsEntry("operator", "demo");
        assertThat(actionStepInstanceRepository.saved).hasSize(2);
        assertThat(actionStepInstanceRepository.saved.get(0).stepName()).isEqualTo("send-cancel-event");
        assertThat(actionStepInstanceRepository.saved.get(0).status()).isEqualTo(ActionStepStatus.PENDING);
        assertThat(actionStepInstanceRepository.saved.get(0).payload()).containsEntry("orderId", "1");
        assertThat(actionStepInstanceRepository.saved.get(1).stepType()).isEqualTo("SMS");
        assertThat(actionOutboxRepository.saved).isNotNull();
        assertThat(actionOutboxRepository.saved.topic()).isEqualTo("ACTION_EXECUTE");
        assertThat(actionOutboxRepository.saved.status()).isEqualTo(ActionOutboxStatus.NEW);
        assertThat(actionOutboxRepository.saved.actionInstanceId()).isEqualTo(actionInstanceRepository.saved.id());
    }

    private static final class CapturingActionInstanceRepository implements ActionInstanceRepository {
        private ActionInstance saved;

        @Override
        public Optional<ActionInstance> findById(String id) {
            return saved != null && saved.id().equals(id) ? Optional.of(saved) : Optional.empty();
        }

        @Override
        public Optional<ActionInstance> findByActionNameAndBizKey(String actionName, String bizKey) {
            return Optional.empty();
        }

        @Override
        public ActionInstance save(ActionInstance instance) {
            this.saved = instance;
            return instance;
        }
    }

    private static final class CapturingActionStepInstanceRepository implements ActionStepInstanceRepository {
        private List<ActionStepInstance> saved = List.of();

        @Override
        public ActionStepInstance save(ActionStepInstance stepInstance) {
            this.saved = this.saved.stream()
                    .filter(existing -> !existing.id().equals(stepInstance.id()))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            this.saved.add(stepInstance);
            return stepInstance;
        }

        @Override
        public List<ActionStepInstance> saveAll(List<ActionStepInstance> stepInstances) {
            this.saved = new ArrayList<>(stepInstances);
            return stepInstances;
        }

        @Override
        public List<ActionStepInstance> findByActionInstanceId(String actionInstanceId) {
            return saved.stream().filter(step -> step.actionInstanceId().equals(actionInstanceId)).toList();
        }
    }

    private static final class CapturingActionOutboxRepository implements ActionOutboxRepository {
        private ActionOutbox saved;

        @Override
        public ActionOutbox save(ActionOutbox outbox) {
            this.saved = outbox;
            return outbox;
        }

        @Override
        public Optional<ActionOutbox> findByActionInstanceId(String actionInstanceId) {
            return saved != null && saved.actionInstanceId().equals(actionInstanceId) ? Optional.of(saved) : Optional.empty();
        }
    }
}
