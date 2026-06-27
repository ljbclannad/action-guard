package io.github.actionguard.im;

public interface ImGroupCreateSender {

    String provider();

    ImActionResult create(ImGroupCreateRequest request);
}
