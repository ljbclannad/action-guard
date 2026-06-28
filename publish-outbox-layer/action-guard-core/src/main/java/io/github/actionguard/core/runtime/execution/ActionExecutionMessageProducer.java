package io.github.actionguard.core.runtime.execution;

import io.github.actionguard.core.model.ActionOutbox;

public interface ActionExecutionMessageProducer {

    void publish(ActionOutbox outbox);
}
