package io.github.actionguard.core.runtime.execution;

import io.github.actionguard.api.runtime.StepExecutionResult;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.model.ActionStepStatus;
import io.github.actionguard.core.repository.ActionStepInstanceRepository;
import io.github.actionguard.core.runtime.state.ActionTransitionContext;
import io.github.actionguard.core.runtime.state.ActionTransitionEvent;
import io.github.actionguard.core.runtime.state.ActionTransitionExecution;
import io.github.actionguard.core.runtime.state.ActionTransitionMetadata;
import io.github.actionguard.core.runtime.state.ActionTransitionService;

import java.time.Instant;
import java.util.Objects;

/**
 * Action 执行期运行时服务。
 *
 * <p>它处在 {@code callback -> step persistence -> action transition} 这半段链路上：
 * 执行回调已经判断出当前 step 成功、可重试失败或终态失败之后，会把这类“执行后状态落库”的
 * 组合动作交给这里。
 *
 * <p>这样 callback 本身只保留流程编排和重试/调度决策，而 step 实例如何持久化、随后如何推进
 * action 状态，则集中在一个稳定入口里，避免成功分支和失败分支各自重复拼装持久化逻辑。
 */
public class ActionExecutionRuntimeService {

    private final ActionStepInstanceRepository actionStepInstanceRepository;
    private final ActionTransitionService actionTransitionService;

    public ActionExecutionRuntimeService(
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionTransitionService actionTransitionService
    ) {
        this.actionStepInstanceRepository = Objects.requireNonNull(actionStepInstanceRepository, "actionStepInstanceRepository must not be null");
        this.actionTransitionService = Objects.requireNonNull(actionTransitionService, "actionTransitionService must not be null");
    }

    public ActionExecutionProgress completeStepSuccess(
            ActionInstance actionInstance,
            ActionStepInstance currentStep,
            int nextStepIndex,
            Instant occurredAt
    ) {
        ActionStepInstance persistedStep = actionStepInstanceRepository.save(new ActionStepInstance(
                currentStep.id(),
                currentStep.actionInstanceId(),
                currentStep.stepIndex(),
                currentStep.stepName(),
                currentStep.stepType(),
                currentStep.target(),
                ActionStepStatus.SUCCESS,
                currentStep.attemptCount() + 1,
                currentStep.payload(),
                null,
                null,
                currentStep.version(),
                currentStep.createdAt(),
                occurredAt
        ));
        ActionTransitionExecution transitionExecution = actionTransitionService.transition(
                actionInstance,
                ActionTransitionEvent.STEP_SUCCEEDED,
                ActionTransitionContext.atNextStep(nextStepIndex, occurredAt),
                ActionTransitionMetadata.of(
                        currentStep.stepIndex(),
                        currentStep.stepName(),
                        currentStep.stepType(),
                        null,
                        null,
                        null
                )
        );
        return new ActionExecutionProgress(persistedStep, transitionExecution);
    }

    public ActionStepInstance persistFailedStep(
            ActionStepInstance currentStep,
            StepExecutionResult result,
            String errorMessage,
            Instant occurredAt
    ) {
        return actionStepInstanceRepository.save(new ActionStepInstance(
                currentStep.id(),
                currentStep.actionInstanceId(),
                currentStep.stepIndex(),
                currentStep.stepName(),
                currentStep.stepType(),
                currentStep.target(),
                ActionStepStatus.FAILED,
                currentStep.attemptCount() + 1,
                currentStep.payload(),
                result.errorCode(),
                errorMessage,
                currentStep.version(),
                currentStep.createdAt(),
                occurredAt
        ));
    }

    public ActionTransitionExecution transitionFailure(
            ActionInstance actionInstance,
            ActionStepInstance failedStep,
            StepExecutionResult result,
            String errorMessage,
            ActionTransitionEvent transitionEvent,
            Instant occurredAt
    ) {
        ActionTransitionExecution transitionExecution = actionTransitionService.transition(
                actionInstance,
                transitionEvent,
                ActionTransitionContext.failure(failedStep.stepIndex(), result.errorCode(), errorMessage, occurredAt),
                ActionTransitionMetadata.of(
                        failedStep.stepIndex(),
                        failedStep.stepName(),
                        failedStep.stepType(),
                        null,
                        result.errorCode(),
                        errorMessage
                )
        );
        return transitionExecution;
    }
}
