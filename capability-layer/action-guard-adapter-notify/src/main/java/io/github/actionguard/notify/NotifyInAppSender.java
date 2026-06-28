package io.github.actionguard.notify;

public interface NotifyInAppSender {

    String provider();

    NotifySendResult send(NotifyInAppRequest request);
}
