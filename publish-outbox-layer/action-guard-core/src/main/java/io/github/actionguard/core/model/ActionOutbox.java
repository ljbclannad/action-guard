package io.github.actionguard.core.model;

import java.time.Instant;

/**
 * Action 执行消息的可靠投递记录，与业务数据及执行实例一同落库，供首次投递、步骤推进和恢复扫描使用。
 *
 * <p>记录消息发布状态，不表示消费结果；同一记录可在步骤推进或业务重试时承载新的逻辑投递任务。
 *
 * @param id Outbox 记录唯一标识，默认发布器生成 UUID
 * @param actionInstanceId 所属 Action 实例标识
 * @param topic 执行消息的逻辑主题，由消息通道适配器解释和路由
 * @param dispatchId 本轮逻辑投递标识，用于生成消息标识；传输失败重发时保留，推进下一步骤或调度业务重试时重新生成
 * @param status 消息发布状态，不等同于 Action 或步骤执行状态
 * @param availableAt 任务可调度时间，用于控制延迟重试与恢复扫描候选范围
 * @param attemptCount 累计计数，初始为 {@code 0}；当前投递失败回退和业务重试调度都会加一，
 *                     正常步骤推进保留原值，因此不能解释为纯 MQ 失败次数或步骤执行次数
 * @param version 持久化乐观锁版本号，成功更新时递增，用于投递抢占与并发冲突检测
 * @param createdAt 记录创建时间，后续重新调度保留此值
 * @param updatedAt 最近更新时间，恢复扫描结合超时规则判断能否接管已抢占记录
 */
public record ActionOutbox(
        String id,
        String actionInstanceId,
        String topic,
        String dispatchId,
        ActionOutboxStatus status,
        Instant availableAt,
        int attemptCount,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
    public ActionOutbox(
            String id, String actionInstanceId, String topic, ActionOutboxStatus status,
            Instant availableAt, int attemptCount, int version, Instant createdAt, Instant updatedAt
    ) {
        this(id, actionInstanceId, topic, id, status, availableAt, attemptCount, version, createdAt, updatedAt);
    }
}
