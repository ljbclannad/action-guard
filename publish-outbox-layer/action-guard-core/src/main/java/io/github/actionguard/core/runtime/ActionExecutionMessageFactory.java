package io.github.actionguard.core.runtime;

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
        return outbox.topic() + ":" + outbox.id();
    }

    public String messageKey(ActionOutbox outbox) {
        return outbox.topic() + ":" + outbox.actionInstanceId();
    }
}
