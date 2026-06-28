package io.github.actionguard.notify.sender;

import io.github.actionguard.notify.model.NotifyInAppRequest;
import io.github.actionguard.notify.model.NotifySendResult;

public interface NotifyInAppSender {

    String provider();

    NotifySendResult send(NotifyInAppRequest request);
}
