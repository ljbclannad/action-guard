package io.github.actionguard.core.runtime.execution;

import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.runtime.state.ActionTransitionExecution;

/**
 * 单次 step 执行完成后的持久化结果。
 *
 * <p>它把“更新后的 step 实例”和“关联的 action 迁移结果”打包返回给回调编排层，
 * 这样上层既能继续做指标、重试和调度决策，也不必重新查询仓储来确认刚刚落下去的状态。
 */
public record ActionExecutionProgress(
        ActionStepInstance stepInstance,
        ActionTransitionExecution transitionExecution
) {
}
