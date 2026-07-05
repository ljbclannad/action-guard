package io.github.actionguard.core.runtime.state;

import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionStatus;

/**
 * Action 迁移结果。
 *
 * <p>它处在 {@code state machine -> caller} 这半段链路上：调用方把当前动作和一个
 * {@link ActionTransitionEvent} 交给状态机后，拿回来的不只是更新后的 {@link ActionInstance}，
 * 还包括这次迁移的起点状态、终点状态和触发事件。
 *
 * <p>这样调用方在持久化状态之外，还可以把同一份结果继续喂给审计、指标、告警或时间线组件，
 * 避免这些外围能力各自再推断一遍“刚才到底发生了什么迁移”。
 */
public record ActionTransitionResult(
        ActionStatus fromStatus,
        ActionStatus toStatus,
        ActionTransitionEvent event,
        ActionInstance actionInstance
) {
}
