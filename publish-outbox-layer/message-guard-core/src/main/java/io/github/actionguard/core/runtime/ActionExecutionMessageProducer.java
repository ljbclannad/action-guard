package io.github.actionguard.core.runtime;

import io.github.actionguard.core.model.ActionOutbox;

public interface ActionExecutionMessageProducer {

    void publish(ActionOutbox outbox);
}
