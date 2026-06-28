package io.github.actionguard.core.runtime;

import io.github.actionguard.api.ActionPublisher;
import io.github.actionguard.api.ActionRequest;
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

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class DefaultActionPublisher implements ActionPublisher {

    private static final String ACTION_EXECUTE_TOPIC = "ACTION_EXECUTE";

    private final ActionDefinitionRegistry definitionRegistry;
    private final ActionInstanceRepository actionInstanceRepository;
    private final ActionStepInstanceRepository actionStepInstanceRepository;
    private final ActionOutboxRepository actionOutboxRepository;
    private final Clock clock;

    public DefaultActionPublisher(
            ActionDefinitionRegistry definitionRegistry,
            ActionInstanceRepository actionInstanceRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionOutboxRepository actionOutboxRepository,
            Clock clock
    ) {
        this.definitionRegistry = Objects.requireNonNull(definitionRegistry, "definitionRegistry must not be null");
        this.actionInstanceRepository = Objects.requireNonNull(actionInstanceRepository, "actionInstanceRepository must not be null");
        this.actionStepInstanceRepository = Objects.requireNonNull(actionStepInstanceRepository, "actionStepInstanceRepository must not be null");
        this.actionOutboxRepository = Objects.requireNonNull(actionOutboxRepository, "actionOutboxRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void publish(ActionRequest request) {
        validateRequest(request);

        ActionDefinition definition = definitionRegistry.getRequired(request.actionName());
        Instant now = clock.instant();
        String actionInstanceId = UUID.randomUUID().toString();

        ActionInstance actionInstance = new ActionInstance(
                actionInstanceId,
                definition.name(),
                request.bizKey(),
                ActionStatus.NEW,
                0,
                definition.steps().size(),
                safeAttributes(request.attributes()),
                null,
                null,
                0,
                now,
                now
        );
        List<ActionStepInstance> stepInstances = buildStepInstances(actionInstanceId, definition.steps(), safeStepPayloads(request), now);
        ActionOutbox outbox = new ActionOutbox(
                UUID.randomUUID().toString(),
                actionInstanceId,
                ACTION_EXECUTE_TOPIC,
                ActionOutboxStatus.NEW,
                now,
                0,
                0,
                now,
                now
        );

        actionInstanceRepository.save(actionInstance);
        actionStepInstanceRepository.saveAll(stepInstances);
        actionOutboxRepository.save(outbox);
    }

    private void validateRequest(ActionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.actionName() == null || request.actionName().isBlank()) {
            throw new IllegalArgumentException("actionName must not be blank");
        }
        if (request.bizKey() == null || request.bizKey().isBlank()) {
            throw new IllegalArgumentException("bizKey must not be blank");
        }
    }

    private Map<String, Object> safeAttributes(Map<String, Object> attributes) {
        return attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    private List<Map<String, Object>> safeStepPayloads(ActionRequest request) {
        if (request.steps() == null || request.steps().isEmpty()) {
            return List.of();
        }
        return request.steps().stream()
                .map(step -> step.payload() == null ? Map.<String, Object>of() : Map.copyOf(step.payload()))
                .toList();
    }

    private List<ActionStepInstance> buildStepInstances(
            String actionInstanceId,
            List<ActionStepDefinition> steps,
            List<Map<String, Object>> requestStepPayloads,
            Instant now
    ) {
        return java.util.stream.IntStream.range(0, steps.size())
                .mapToObj(index -> {
                    ActionStepDefinition step = steps.get(index);
                    Map<String, Object> payload = index < requestStepPayloads.size() ? requestStepPayloads.get(index) : Map.of();
                    return new ActionStepInstance(
                            UUID.randomUUID().toString(),
                            actionInstanceId,
                            index,
                            step.name(),
                            step.stepType(),
                            step.target(),
                            ActionStepStatus.PENDING,
                            0,
                            payload,
                            null,
                            null,
                            0,
                            now,
                            now
                    );
                })
                .toList();
    }
}
