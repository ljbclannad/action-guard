package io.github.actionguard.ops.api.support;

import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.runtime.state.ActionCommand;
import io.github.actionguard.core.runtime.state.ActionStateMachine;

public class ActionCommandValidator {

    public void validateRetry(ActionStatus status) {
        ActionStateMachine.assertCommandAllowed(status, ActionCommand.RETRY);
    }

    public void validateSkip(ActionStatus status) {
        ActionStateMachine.assertCommandAllowed(status, ActionCommand.SKIP);
    }

    public void validateCancel(ActionStatus status) {
        ActionStateMachine.assertCommandAllowed(status, ActionCommand.CANCEL);
    }

    public void validateCompensate(ActionStatus status) {
        ActionStateMachine.assertCommandAllowed(status, ActionCommand.COMPENSATE);
    }
}
