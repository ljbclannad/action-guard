package io.github.actionguard.core.runtime;

import io.github.actionguard.api.ActionPublisher;
import io.github.actionguard.api.ActionRequest;

public class DefaultActionPublisher implements ActionPublisher {

    @Override
    public void publish(ActionRequest request) {
        if (request.actionName() == null || request.actionName().isBlank()) {
            throw new IllegalArgumentException("actionName must not be blank");
        }
    }
}
