package io.github.actionguard.im;

public interface ImGroupMessageSender {

    String provider();

    ImActionResult send(ImGroupMessageSendRequest request);
}
