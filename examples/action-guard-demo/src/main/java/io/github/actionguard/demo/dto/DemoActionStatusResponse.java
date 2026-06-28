package io.github.actionguard.demo.dto;

public record DemoActionStatusResponse(
        String actionInstanceId,
        String actionName,
        String bizKey,
        String status
) {
}
