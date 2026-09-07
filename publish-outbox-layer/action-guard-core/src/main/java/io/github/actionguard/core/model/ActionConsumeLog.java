package io.github.actionguard.core.model;

import java.time.Instant;

/**
 * 执行消息的消费记录，保存消费抢占、去重和处理结果，不等同于业务步骤执行记录。
 *
 * @param id 消费记录唯一标识
 * @param messageId 消息唯一标识，当前 Repository 按此标识查找和抢占消费记录
 * @param actionInstanceId 消息关联的 Action 实例标识
 * @param consumerGroup 消费组标识，用于记录处理方；当前 Repository 并非按消费组与消息标识组合查找
 * @param consumeStatus 消费处理状态，不代表整个 Action 的执行结果
 * @param dedupeKey 从消息的 {@code messageKey} 复制的去重关联信息，不是 Action 发布请求的幂等键
 * @param attemptCount 首次消费为 {@code 1}，失败后重新抢占及标记重复跳过时递增；不是 Handler 实际执行次数
 * @param lastErrorMessage 最近记录的消费错误或重复跳过原因，无错误时可为 {@code null}
 * @param version 持久化乐观锁版本号，用于检测并发更新
 * @param firstReceivedAt 首次创建消费记录的时间，后续更新保留此值
 * @param lastReceivedAt 最近记录的接收或处理时间；当前状态更新也会刷新，不能视为精确的 MQ 最近到达时间
 * @param updatedAt 消费记录最近更新时间
 */
public record ActionConsumeLog(
        String id,
        String messageId,
        String actionInstanceId,
        String consumerGroup,
        ActionConsumeStatus consumeStatus,
        String dedupeKey,
        int attemptCount,
        String lastErrorMessage,
        int version,
        Instant firstReceivedAt,
        Instant lastReceivedAt,
        Instant updatedAt
) {
}
