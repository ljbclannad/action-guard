package io.github.actionguard.im.sender;

import io.github.actionguard.im.model.ImActionResult;
import io.github.actionguard.im.model.ImGroupMessageSendRequest;

public interface ImGroupMessageSender {

    String provider();

    ImActionResult send(ImGroupMessageSendRequest request);
}
