package io.github.actionguard.core.model;

import java.time.Instant;

/**
 * 单个步骤的补偿处理日志，记录成功、失败或跳过结果，并为中断恢复提供已处理步骤信息。
 *
 * @param id 补偿日志唯一标识，补偿服务生成 UUID
 * @param compensationBatchId 补偿批次标识，当前按 {@code "batch-" + actionInstanceId} 生成，
 *                            不代表每次补偿调用都会创建新的批次
 * @param actionInstanceId 所属 Action 实例标识
 * @param actionStepInstanceId 本次补偿涉及的步骤实例标识
 * @param stepIndex 原流程中从 {@code 0} 开始的步骤索引，补偿按已成功步骤的索引倒序执行
 * @param stepName 被补偿步骤的名称
 * @param stepType 被补偿步骤的类型
 * @param compensationStatus 本条日志的补偿结果，当前写入 {@code SUCCESS}、{@code FAILED} 或 {@code SKIPPED}
 * @param compensatorName 补偿器实现类的全限定名，未注册补偿器而跳过时为 {@code null}
 * @param resultMessage 补偿器返回的结果说明，或框架记录的跳过原因
 * @param createdAt 日志创建时间
 * @param updatedAt 日志更新时间，当前补偿服务新增日志时与 {@code createdAt} 相同
 */
public record ActionCompensationLog(
        String id,
        String compensationBatchId,
        String actionInstanceId,
        String actionStepInstanceId,
        int stepIndex,
        String stepName,
        String stepType,
        String compensationStatus,
        String compensatorName,
        String resultMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
