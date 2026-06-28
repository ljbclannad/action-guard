package io.github.actionguard.notify.sender;

import io.github.actionguard.notify.model.NotifySendResult;
import io.github.actionguard.notify.model.NotifySmsRequest;

public interface NotifySmsSender {

    String provider();

    NotifySendResult send(NotifySmsRequest request);
}
