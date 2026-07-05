package io.github.actionguard.core.runtime.state;

/**
 * Action 迁移附加元数据。
 *
 * <p>它处在 {@code caller -> transition service} 这半段链路上：状态机本身只关心“当前状态 +
 * 迁移事件 + 最小上下文”，而时间线、排障和审计还需要额外知道这次迁移关联的是哪个 step、
 * 由谁触发、是否带了错误码和错误信息。
 *
 * <p>因此调用方把这些外围元数据统一装进这里，再交给 {@link ActionTransitionService} 去完成
 * 持久化与观测，避免各个调用点重复组装 {@code ActionTransitionLog}。
 */
public record ActionTransitionMetadata(
        Integer stepIndex,
        String stepName,
        String stepType,
        String operator,
        String errorCode,
        String errorMessage
) {

    public static ActionTransitionMetadata of(
            Integer stepIndex,
            String stepName,
            String stepType,
            String operator,
            String errorCode,
            String errorMessage
    ) {
        return new ActionTransitionMetadata(stepIndex, stepName, stepType, operator, errorCode, errorMessage);
    }
}
