package io.github.actionguard.im;

public interface ImGroupInviteSender {

    String provider();

    ImActionResult invite(ImGroupInviteRequest request);
}
