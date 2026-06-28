package io.github.actionguard.notify;

public interface NotifySmsSender {

    String provider();

    NotifySendResult send(NotifySmsRequest request);
}
