package io.github.actionguard.notify.model;

public record NotifySendResult(
        boolean success,
        String errorCode,
        String errorMessage
) {

    public static NotifySendResult succeeded() {
        return new NotifySendResult(true, null, null);
    }

    public static NotifySendResult failed(String errorCode, String errorMessage) {
        return new NotifySendResult(false, errorCode, errorMessage);
    }
}
