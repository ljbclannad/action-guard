package io.github.actionguard.demo;

public record DemoPublishResponse(
        String actionInstanceId,
        String actionName,
        String bizKey,
        String status
) {
}
