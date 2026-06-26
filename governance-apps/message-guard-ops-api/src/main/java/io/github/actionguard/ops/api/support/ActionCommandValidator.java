package io.github.actionguard.ops.api.support;

import io.github.actionguard.core.model.ActionStatus;

public class ActionCommandValidator {

    public void validateRetry(ActionStatus status) {
        if (status != ActionStatus.FAILED && status != ActionStatus.RETRYING) {
            throw new IllegalStateException("Retry is not allowed for status: " + status);
        }
    }

    public void validateSkip(ActionStatus status) {
        if (status != ActionStatus.DISPATCHING && status != ActionStatus.RETRYING) {
            throw new IllegalStateException("Skip is not allowed for status: " + status);
        }
    }

    public void validateCancel(ActionStatus status) {
        if (status != ActionStatus.NEW && status != ActionStatus.DISPATCHING && status != ActionStatus.RETRYING) {
            throw new IllegalStateException("Cancel is not allowed for status: " + status);
        }
    }

    public void validateCompensate(ActionStatus status) {
        if (status != ActionStatus.FAILED && status != ActionStatus.DEAD) {
            throw new IllegalStateException("Compensate is not allowed for status: " + status);
        }
    }
}
