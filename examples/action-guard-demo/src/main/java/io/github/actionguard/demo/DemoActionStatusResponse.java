package io.github.actionguard.demo;

public record DemoActionStatusResponse(
        String actionInstanceId,
        String actionName,
        String bizKey,
        String status
) {
}
