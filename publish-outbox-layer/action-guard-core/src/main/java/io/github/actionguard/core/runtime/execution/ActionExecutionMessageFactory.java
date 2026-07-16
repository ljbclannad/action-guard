package io.github.actionguard.core.runtime.execution;

import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.core.model.ActionOutbox;

public class ActionExecutionMessageFactory {

    public ActionExecutionMessage create(ActionOutbox outbox) {
        return new ActionExecutionMessage(
                messageId(outbox),
                messageKey(outbox),
                outbox.id(),
                outbox.actionInstanceId(),
                outbox.topic(),
                outbox.createdAt()
        );
    }

    public String messageId(ActionOutbox outbox) {
        // A dispatch is immutable from the consumer's point of view. MQ retries keep this id,
        // while a next step or a business retry receives a new dispatch id.
        return outbox.topic() + ":" + outbox.dispatchId();
    }

    public String messageKey(ActionOutbox outbox) {
        return outbox.topic() + ":" + outbox.actionInstanceId();
    }
}
