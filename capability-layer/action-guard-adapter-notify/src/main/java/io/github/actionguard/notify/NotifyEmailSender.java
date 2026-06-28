package io.github.actionguard.notify;

public interface NotifyEmailSender {

    String provider();

    NotifySendResult send(NotifyEmailRequest request);
}
