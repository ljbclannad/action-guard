package io.github.actionguard.demo.controller;

import io.github.actionguard.api.ActionPublication;
import io.github.actionguard.api.ActionPublisher;
import io.github.actionguard.api.ActionRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@Profile("fault-demo")
public class DemoFaultController {
    private final ActionPublisher publisher;

    public DemoFaultController(ActionPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/api/demo/scenarios/{scenario}")
    public ActionPublication publish(@PathVariable String scenario) {
        String actionName = switch (scenario) {
            case "auto-retry" -> "demo-auto-retry";
            case "manual-skip" -> "demo-manual-skip";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知的故障演示场景");
        };
        return publisher.publish(new ActionRequest(actionName, "demo:" + UUID.randomUUID(), Map.of(), List.of()));
    }
}
