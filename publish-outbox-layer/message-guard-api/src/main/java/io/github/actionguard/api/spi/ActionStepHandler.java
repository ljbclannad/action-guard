package io.github.actionguard.api.spi;

import io.github.actionguard.api.runtime.ActionStepContext;
import io.github.actionguard.api.runtime.StepExecutionResult;

public interface ActionStepHandler {

    String stepType();

    StepExecutionResult execute(ActionStepContext context);
}
