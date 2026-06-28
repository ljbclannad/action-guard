package io.github.actionguard.notify.sender;

import io.github.actionguard.notify.model.NotifyEmailRequest;
import io.github.actionguard.notify.model.NotifySendResult;

public interface NotifyEmailSender {

    String provider();

    NotifySendResult send(NotifyEmailRequest request);
}
