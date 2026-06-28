package io.github.actionguard.demo.dto;

public record DemoPublishResponse(
        String actionInstanceId,
        String actionName,
        String bizKey,
        String status
) {
}
