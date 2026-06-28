package io.github.actionguard.demo.dto;

public record DemoPublishRequest(
        String actionName,
        String bizKey,
        String phoneNumber
) {
}
