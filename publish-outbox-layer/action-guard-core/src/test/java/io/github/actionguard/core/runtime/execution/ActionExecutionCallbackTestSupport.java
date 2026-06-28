package io.github.actionguard.core.runtime.execution;

import io.github.actionguard.api.runtime.ActionExecutionMessage;

import java.util.ArrayList;
import java.util.List;

public class ActionExecutionCallbackTestSupport implements ActionExecutionCallback {

    private final List<ActionExecutionMessage> received = new ArrayList<>();

    @Override
    public void execute(ActionExecutionMessage message) {
        received.add(message);
    }

    public List<ActionExecutionMessage> received() {
        return List.copyOf(received);
    }
}
