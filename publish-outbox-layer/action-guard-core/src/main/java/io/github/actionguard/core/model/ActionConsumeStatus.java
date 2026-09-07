package io.github.actionguard.core.model;

/** 消息消费记录的状态，与 Action 和步骤状态独立。 */
public enum ActionConsumeStatus {
    /** 已接收；当前消费抢占实现直接创建 EXECUTING 记录，未使用此中间状态。 */
    RECEIVED,
    /** 已取得本次消费处理权，正在处理消息。 */
    EXECUTING,
    /** 已记录确认处理结果，不代表整个 Action 执行完成。 */
    ACKED,
    /** 重复投递已跳过，不再重复执行业务回调。 */
    DUPLICATE_SKIPPED,
    /** 消费处理失败，当前 Repository 允许再次抢占此状态的记录。 */
    FAILED,
    /** 已记录死信处理结果。 */
    DEAD_LETTERED
}
