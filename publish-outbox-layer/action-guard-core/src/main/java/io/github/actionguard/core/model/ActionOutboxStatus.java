package io.github.actionguard.core.model;

/** Outbox 发布状态，只描述消息发送与发布落库，不描述消费完成情况。 */
public enum ActionOutboxStatus {
    /** 待投递；首次创建、后续任务调度或发送失败回退时使用。 */
    NEW,
    /** 已通过乐观锁抢占；仅在恢复扫描确认超时后允许重新接管。 */
    CLAIMED,
    /** 消息发送成功且发布状态已落库，仍存在 MQ 与数据库之间的重复投递窗口。 */
    DONE,
    /** 不再正常投递的状态；当前统一投递器发送失败回退 NEW，不以此状态结束传输重试。 */
    DEAD
}
