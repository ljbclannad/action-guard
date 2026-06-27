package io.github.actionguard.api.spi;

import io.github.actionguard.api.runtime.ActionAlertEvent;
import io.github.actionguard.api.runtime.ActionAlertLevel;
import io.github.actionguard.api.runtime.ActionAlertType;

import java.time.Instant;
import java.util.Map;

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
