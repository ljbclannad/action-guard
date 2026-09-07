package io.github.actionguard.core.model;

import java.time.Instant;

/**
 * 按 Action 名称配置的持久化治理策略，用于覆盖定义中的部分默认行为。
 *
 * @param id 治理策略记录唯一标识
 * @param actionName 策略对应的 Action 定义名称
 * @param compensationEnabled 补偿开关覆盖值，非 {@code null} 时优先于定义中的开关；
 *                            为 {@code null} 时回退到定义，开启不等于失败后自动补偿
 * @param retryPolicyJson 重试策略 JSON，当前仅持久化，尚未接入运行时重试决策
 * @param alertPolicyJson 告警策略 JSON，当前仅持久化，尚未接入运行时告警决策
 * @param updatedAt 策略最近更新时间
 */
public record ActionGovernancePolicy(
        String id,
        String actionName,
        Boolean compensationEnabled,
        String retryPolicyJson,
        String alertPolicyJson,
        Instant updatedAt
) {
}
