package io.github.actionguard.core.runtime.state;

import io.github.actionguard.core.model.ActionTransitionLog;

/**
 * Action 迁移执行结果。
 *
 * <p>它处在 {@code transition service -> caller} 这半段链路上：调用方触发一次迁移后，
 * 除了需要拿到更新后的 action 状态，还经常要继续判断“是否进入 SUCCESS / DISPATCHING /
 * COMPENSATING”等后续分支。
 *
 * <p>所以这里把“已持久化的迁移结果”和“对应的迁移日志”一起返回，调用方可以继续推进业务，
 * 同时不需要再次查询仓储确认刚刚落下去的状态。
 */
public record ActionTransitionExecution(
        ActionTransitionResult transitionResult,
        ActionTransitionLog transitionLog
) {
}
