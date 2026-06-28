package io.github.actionguard.im.sender;

import io.github.actionguard.im.model.ImActionResult;
import io.github.actionguard.im.model.ImGroupInviteRequest;

public interface ImGroupInviteSender {

    String provider();

    ImActionResult invite(ImGroupInviteRequest request);
}
