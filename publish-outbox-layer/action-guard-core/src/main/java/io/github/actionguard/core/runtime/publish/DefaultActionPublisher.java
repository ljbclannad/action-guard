package io.github.actionguard.core.runtime.publish;

import io.github.actionguard.api.ActionPublisher;
import io.github.actionguard.api.ActionPublication;
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
import io.github.actionguard.core.runtime.definition.ActionDefinitionRegistry;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Action 发布主链路的默认实现。
 *
 * <p>它处在“业务请求 -> Action 运行时”的入口位置，职责非常收敛：
 * 根据 actionName 读取已注册的 {@link ActionDefinition}，生成一条动作实例、
 * 多条步骤实例，以及一条用于驱动后续执行的 outbox 记录，并在同一事务语义下完成持久化。
 *
 * <p>这个类不直接执行任何 step，也不关心 MQ 细节。后续真正的投递与执行会由
 * starter 层包装器、outbox recovery 链路和 {@code ActionExecutionCallback} 继续推进。
 * 这样发布路径只负责“可靠落库”，把主交易提交和异步执行解耦开。
 */
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
    public ActionPublication publish(ActionRequest request) {
        validateRequest(request);

        ActionDefinition definition = definitionRegistry.getRequired(request.actionName());
        String idempotencyKey = resolvedIdempotencyKey(request);
        var existing = actionInstanceRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return new ActionPublication(existing.orElseThrow().id(), true);
        }
        Instant now = clock.instant();
        String actionInstanceId = UUID.randomUUID().toString();

        // 发布主路径只负责在同一事务里落库，不直接执行后续 step。
        // 这样可以保证主交易提交成功时，动作实例、步骤实例和 outbox 记录要么一起成功，要么一起回滚。
        ActionInstance actionInstance = new ActionInstance(
                actionInstanceId,
                definition.name(),
                definition.version(),
                request.bizKey(),
                idempotencyKey,
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
                UUID.randomUUID().toString(),
                ActionOutboxStatus.NEW,
                now,
                0,
                0,
                now,
                now
        );

        // 保持固定写入顺序，便于后续排查主链路问题时从 action -> step -> outbox 顺着追踪。
        try {
            actionInstanceRepository.save(actionInstance);
            actionStepInstanceRepository.saveAll(stepInstances);
            actionOutboxRepository.save(outbox);
        } catch (DuplicateKeyException ex) {
            return actionInstanceRepository.findByIdempotencyKey(idempotencyKey)
                    .map(instance -> new ActionPublication(instance.id(), true))
                    .orElseThrow(() -> ex);
        }
        return new ActionPublication(actionInstanceId, false);
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

    private String resolvedIdempotencyKey(ActionRequest request) {
        return request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                ? request.actionName() + ":" + request.bizKey()
                : request.idempotencyKey();
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
        // step 实例以定义文件中的顺序固化下来，后续 runtime 只按 stepIndex 严格串行推进。
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
