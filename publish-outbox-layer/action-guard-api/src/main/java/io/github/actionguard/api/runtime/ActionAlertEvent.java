package io.github.actionguard.api.runtime;

import java.time.Instant;
import java.util.Map;

/**
 * 标准化的 Action 告警事件。
 *
 * <p>{@code eventId} 是单个可靠告警记录的稳定外部投递标识。接收端应以该值去重，
 * 因为发送成功但 DONE 状态更新失败时，框架可能再次投递同一事件。</p>
 */
public record ActionAlertEvent(
        String eventId,
        ActionAlertType type,
        ActionAlertLevel level,
        String title,
        String message,
        String actionName,
        String actionInstanceId,
        String stepName,
        String stepType,
        Instant occurredAt,
        Map<String, String> details
) {
    /**
     * 保持既有调用方的构造方式；可靠告警 Outbox 会在入队时生成稳定的 eventId。
     */
    public ActionAlertEvent(
            ActionAlertType type,
            ActionAlertLevel level,
            String title,
            String message,
            String actionName,
            String actionInstanceId,
            String stepName,
            String stepType,
            Instant occurredAt,
            Map<String, String> details
    ) {
        this(null, type, level, title, message, actionName, actionInstanceId, stepName, stepType, occurredAt, details);
    }
}
