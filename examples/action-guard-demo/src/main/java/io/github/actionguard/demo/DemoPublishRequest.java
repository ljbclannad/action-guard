package io.github.actionguard.demo;

public record DemoPublishRequest(
        String actionName,
        String bizKey,
        String phoneNumber
) {
}
