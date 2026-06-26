package io.github.actionguard.api.spi;

import io.github.actionguard.api.runtime.ActionAlertLevel;

public interface ActionAlertPublisher {

    void publish(ActionAlertLevel level, String title, String message);
}
