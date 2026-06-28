package io.github.actionguard.im.sender;

import io.github.actionguard.im.model.ImActionResult;
import io.github.actionguard.im.model.ImGroupCreateRequest;

public interface ImGroupCreateSender {

    String provider();

    ImActionResult create(ImGroupCreateRequest request);
}
