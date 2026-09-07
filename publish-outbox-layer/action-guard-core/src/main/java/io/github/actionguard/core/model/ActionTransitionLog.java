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
 *
 * @param id 迁移日志唯一标识，工厂方法生成 UUID
 * @param actionInstanceId 发生状态迁移的 Action 实例标识
 * @param event 触发本次状态迁移的事件
 * @param fromStatus 迁移前的 Action 状态
 * @param toStatus 迁移后的 Action 状态
 * @param stepIndex 关联步骤的零起始索引，无步骤上下文时可为 {@code null}
 * @param stepName 关联步骤名称，无步骤上下文时可为 {@code null}
 * @param stepType 关联步骤类型，无步骤上下文时可为 {@code null}
 * @param operator 操作人标识，自动迁移等未提供操作人信息的场景可为 {@code null}
 * @param errorCode 本次迁移关联的错误码，无错误信息时可为 {@code null}
 * @param errorMessage 本次迁移关联的错误原因摘要，无错误信息时可为 {@code null}
 * @param createdAt 本次迁移日志记录的时间，由调用方传入
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
