package io.github.actionguard.api;

public interface ActionPublisher {

    ActionPublication publish(ActionRequest request);
}
