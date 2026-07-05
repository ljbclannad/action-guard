package io.github.actionguard.core.model;

import io.github.actionguard.core.runtime.state.ActionTransitionEvent;
import io.github.actionguard.core.runtime.state.ActionTransitionResult;

import java.time.Instant;
import java.util.UUID;

/**
 * Action 状态迁移日志。
 *
 * <p>它处在 {@code transition result -> persistent timeline} 这半段链路上：状态机先产出
 * {@link ActionTransitionResult}，调用方再把这次迁移关联到具体的 action、step、operator
 * 等上下文，落成一条可查询的迁移日志。
 *
 * <p>这样时间线展示、审计排查和离线分析就不需要再去反推多张表里的状态快照，而是可以直接读取
 * “哪条 action 在什么时间，因为哪个事件，从什么状态迁移到了什么状态” 这份事实记录。
 */
public record ActionTransitionLog(
        String id,
        String actionInstanceId,
        ActionTransitionEvent event,
        ActionStatus fromStatus,
        ActionStatus toStatus,
        Integer stepIndex,
        String stepName,
        String stepType,
        String operator,
        String errorCode,
        String errorMessage,
        Instant createdAt
) {

    public static ActionTransitionLog of(
            String actionInstanceId,
            ActionTransitionResult result,
            Integer stepIndex,
            String stepName,
            String stepType,
            String operator,
            String errorCode,
            String errorMessage,
            Instant createdAt
    ) {
        return new ActionTransitionLog(
                UUID.randomUUID().toString(),
                actionInstanceId,
                result.event(),
                result.fromStatus(),
                result.toStatus(),
                stepIndex,
                stepName,
                stepType,
                operator,
                errorCode,
                errorMessage,
                createdAt
        );
    }
}
