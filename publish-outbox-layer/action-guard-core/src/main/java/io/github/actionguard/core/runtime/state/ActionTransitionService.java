package io.github.actionguard.core.runtime.state;

import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionTransitionLog;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionTransitionLogRepository;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;

import java.util.Objects;

/**
 * Action 迁移应用服务。
 *
 * <p>它处在 {@code runtime / governance -> state machine -> persistence} 这条主链路的
 * 收口位置：执行回调、补偿服务、人工治理命令不再各自手写“状态机 apply、保存 action、
 * 保存迁移日志、打指标”这一整套组合动作，而是统一把迁移请求交给这里。
 *
 * <p>这样迁移副作用就被收敛成一个工程化入口。后续如果要补充告警、事件总线、审计增强或
 * 一致性策略，只需要围绕这里扩展，而不是到多个调用点同步修改。
 */
public class ActionTransitionService {

    private final ActionInstanceRepository actionInstanceRepository;
    private final ActionTransitionLogRepository actionTransitionLogRepository;
    private final ActionObservabilityService actionObservabilityService;

    public ActionTransitionService(
            ActionInstanceRepository actionInstanceRepository,
            ActionTransitionLogRepository actionTransitionLogRepository,
            ActionObservabilityService actionObservabilityService
    ) {
        this.actionInstanceRepository = Objects.requireNonNull(actionInstanceRepository, "actionInstanceRepository must not be null");
        this.actionTransitionLogRepository = Objects.requireNonNull(actionTransitionLogRepository, "actionTransitionLogRepository must not be null");
        this.actionObservabilityService = Objects.requireNonNull(actionObservabilityService, "actionObservabilityService must not be null");
    }

    public ActionTransitionExecution transition(
            ActionInstance actionInstance,
            ActionTransitionEvent event,
            ActionTransitionContext context,
            ActionTransitionMetadata metadata
    ) {
        Objects.requireNonNull(actionInstance, "actionInstance must not be null");
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");

        ActionTransitionResult transitionResult = ActionStateMachine.apply(actionInstance, event, context);
        ActionInstance persisted = actionInstanceRepository.save(transitionResult.actionInstance());
        ActionTransitionLog transitionLog = actionTransitionLogRepository.save(ActionTransitionLog.of(
                actionInstance.id(),
                transitionResult,
                metadata.stepIndex(),
                metadata.stepName(),
                metadata.stepType(),
                metadata.operator(),
                metadata.errorCode(),
                metadata.errorMessage(),
                context.occurredAt()
        ));
        ActionTransitionResult persistedResult = new ActionTransitionResult(
                transitionResult.fromStatus(),
                transitionResult.toStatus(),
                transitionResult.event(),
                persisted
        );
        actionObservabilityService.actionTransition(persistedResult);
        return new ActionTransitionExecution(persistedResult, transitionLog);
    }
}
