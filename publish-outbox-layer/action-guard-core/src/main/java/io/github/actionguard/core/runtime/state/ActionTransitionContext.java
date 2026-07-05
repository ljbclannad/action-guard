package io.github.actionguard.core.runtime.state;

import java.time.Instant;

/**
 * Action 迁移上下文。
 *
 * <p>它处在 {@code caller -> state machine} 的参数边界上：某个迁移事件除了“事件名”本身，
 * 往往还需要携带少量运行时上下文，例如推进到哪个 stepIndex、是否附带错误码和错误信息、
 * 以及这次迁移的发生时间。
 *
 * <p>状态机只依赖这里声明的最小上下文来生成迁移结果，而不直接感知 MQ、HTTP、数据库 claim
 * 等外围机制细节，从而把状态推进逻辑保持在一个稳定、可测试的抽象层里。
 */
public record ActionTransitionContext(
        int currentStepIndex,
        String lastErrorCode,
        String lastErrorMessage,
        Instant occurredAt
) {

    public static ActionTransitionContext of(
            int currentStepIndex,
            String lastErrorCode,
            String lastErrorMessage,
            Instant occurredAt
    ) {
        return new ActionTransitionContext(currentStepIndex, lastErrorCode, lastErrorMessage, occurredAt);
    }

    public static ActionTransitionContext atCurrentStep(int currentStepIndex, Instant occurredAt) {
        return new ActionTransitionContext(currentStepIndex, null, null, occurredAt);
    }

    public static ActionTransitionContext atNextStep(int nextStepIndex, Instant occurredAt) {
        return new ActionTransitionContext(nextStepIndex, null, null, occurredAt);
    }

    public static ActionTransitionContext failure(
            int currentStepIndex,
            String lastErrorCode,
            String lastErrorMessage,
            Instant occurredAt
    ) {
        return new ActionTransitionContext(currentStepIndex, lastErrorCode, lastErrorMessage, occurredAt);
    }
}
