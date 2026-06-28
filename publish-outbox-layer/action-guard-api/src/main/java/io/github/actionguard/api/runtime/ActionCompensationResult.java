package io.github.actionguard.api.runtime;

public record ActionCompensationResult(
        boolean success,
        String message
) {

    public static ActionCompensationResult success(String message) {
        return new ActionCompensationResult(true, message);
    }

    public static ActionCompensationResult failure(String message) {
        return new ActionCompensationResult(false, message);
    }
}
