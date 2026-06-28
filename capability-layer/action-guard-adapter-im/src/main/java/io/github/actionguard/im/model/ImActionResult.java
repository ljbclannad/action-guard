package io.github.actionguard.im.model;

public record ImActionResult(
        boolean success,
        String errorCode,
        String errorMessage
) {

    public static ImActionResult succeeded() {
        return new ImActionResult(true, null, null);
    }

    public static ImActionResult failed(String errorCode, String errorMessage) {
        return new ImActionResult(false, errorCode, errorMessage);
    }
}
