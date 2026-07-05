package io.github.actionguard.core.runtime.state;

/**
 * Action 状态迁移事件。
 *
 * <p>它处在 {@code runtime / governance -> state machine} 这段链路上：执行回调、补偿服务、
 * 人工治理命令不会再直接指定“下一个状态应该是什么”，而是先把当前动作上发生的事实
 * 表达成一个 {@code ActionTransitionEvent}，再交给 {@link ActionStateMachine} 解析。
 *
 * <p>这样状态推进的语义就从“状态对状态”的隐式判断，升级成“发生了什么事件，所以状态被推进”的
 * 显式模型。后续如果要做审计、指标、告警或时间线展示，都可以围绕这些标准化事件展开。
 */
public enum ActionTransitionEvent {
    STEP_SUCCEEDED,
    STEP_FAILED_RETRYABLE,
    STEP_FAILED_TERMINAL,
    MANUAL_SKIP_REQUESTED,
    MANUAL_CANCEL_REQUESTED,
    COMPENSATION_STARTED,
    COMPENSATION_SUCCEEDED,
    COMPENSATION_FAILED
}
