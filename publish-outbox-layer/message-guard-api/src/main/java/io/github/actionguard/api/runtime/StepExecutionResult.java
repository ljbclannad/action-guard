package io.github.actionguard.api.runtime;

public record StepExecutionResult(
        boolean success,
        String errorCode,
        String errorMessage
) {

    public static StepExecutionResult succeeded() {
        return new StepExecutionResult(true, null, null);
    }

    public static StepExecutionResult failed(String errorCode, String errorMessage) {
        return new StepExecutionResult(false, errorCode, errorMessage);
    }
}
