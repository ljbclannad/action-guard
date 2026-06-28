package io.github.actionguard.core.runtime.execution;

import io.github.actionguard.api.runtime.ActionExecutionMessage;

public interface ActionExecutionCallback {

    void execute(ActionExecutionMessage message);
}
