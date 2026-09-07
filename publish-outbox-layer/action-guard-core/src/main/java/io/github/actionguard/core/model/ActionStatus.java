package io.github.actionguard.core.model;

import io.github.actionguard.core.runtime.state.ActionStateMachine;

/** Action 整体执行状态，迁移和人工命令的允许范围由 ActionStateMachine 集中维护。 */
public enum ActionStatus {
    /** 已发布并创建实例，尚未完成步骤推进。 */
    NEW(false),
    /** 流程正在推进，不表示消息此刻一定正在发送。 */
    DISPATCHING(false),
    /** 流程成功结束，包括按规则跳过步骤后推进至末尾的情况。 */
    SUCCESS(true),
    /** 当前步骤失败后进入重试流程。 */
    RETRYING(false),
    /** 执行失败，停止自动推进，仍可按治理规则重试或补偿。 */
    FAILED(false),
    /** 当前用于补偿失败后的终止标记，状态机仍允许再次发起补偿。 */
    DEAD(true),
    /** 已进入补偿流程，可能由恢复扫描接续执行。 */
    COMPENSATING(false),
    /** 补偿流程已完成，不代表原业务流程执行成功。 */
    COMPENSATED(true),
    /** 已人工取消或忽略，停止后续正常执行。 */
    IGNORED(true);

    /** 是否标记为终态，不意味着状态机禁止一切后续治理操作。 */
    private final boolean terminal;

    ActionStatus(boolean terminal) {
        this.terminal = terminal;
    }

    /**
     * 返回枚举声明中的终态标记；例如 DEAD 虽为终态，仍可按状态机规则再次补偿。
     *
     * @return 是否标记为终态
     */
    public boolean isTerminal() {
        return terminal;
    }

    /**
     * 查询状态机是否允许状态之间的迁移，不执行迁移或持久化。
     *
     * @param nextStatus 待迁移到的状态
     * @return 是否允许从当前状态迁移到目标状态
     */
    public boolean canTransitionTo(ActionStatus nextStatus) {
        return ActionStateMachine.canTransition(this, nextStatus);
    }
}
