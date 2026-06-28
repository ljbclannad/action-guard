package io.github.actionguard.core.runtime;

import io.github.actionguard.api.runtime.ActionExecutionMessage;

public interface ActionExecutionCallback {

    void execute(ActionExecutionMessage message);
}
