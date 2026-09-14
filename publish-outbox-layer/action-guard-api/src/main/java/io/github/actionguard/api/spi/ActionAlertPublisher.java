package io.github.actionguard.api.spi;

import io.github.actionguard.api.runtime.ActionAlertEvent;
import io.github.actionguard.api.runtime.ActionAlertLevel;
import io.github.actionguard.api.runtime.ActionAlertType;

import java.time.Instant;
import java.util.Map;

/**
 * @deprecated 请迁移到 {@link ActionAlertSender}。该接口保留至兼容窗口结束，starter 不再将其作为直接外发通道。
 */
@Deprecated(since = "0.1.0", forRemoval = false)
public interface ActionAlertPublisher {

    void publish(ActionAlertEvent event);

    default void publish(ActionAlertLevel level, String title, String message) {
        publish(new ActionAlertEvent(
                ActionAlertType.GENERIC,
                level,
                title,
                message,
                null,
                null,
                null,
                null,
                Instant.now(),
                Map.of()
        ));
    }
}
