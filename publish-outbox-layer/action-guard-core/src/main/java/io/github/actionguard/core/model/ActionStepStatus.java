package io.github.actionguard.core.model;

/** 单个步骤的执行状态，不等同于所属 Action 的整体状态。 */
public enum ActionStepStatus {
    /** 步骤已创建，尚未记录执行结果。 */
    PENDING,
    /** 表示执行中；当前执行主链路未单独持久化此中间状态。 */
    RUNNING,
    /** 步骤执行成功，结果已记录。 */
    SUCCESS,
    /** 步骤执行失败，是否重试由运行时策略和 Action 状态共同决定。 */
    FAILED
}
