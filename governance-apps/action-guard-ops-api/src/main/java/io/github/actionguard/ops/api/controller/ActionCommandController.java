package io.github.actionguard.ops.api.controller;

import io.github.actionguard.ops.api.service.ActionCommandService;
import io.github.actionguard.ops.api.support.OperatorResolver;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/actions")
public class ActionCommandController {

    private final ActionCommandService actionCommandService;
    private final OperatorResolver operatorResolver;

    public ActionCommandController(ActionCommandService actionCommandService, OperatorResolver operatorResolver) {
        this.actionCommandService = actionCommandService;
        this.operatorResolver = operatorResolver;
    }

    @PostMapping("/{actionInstanceId}/retry")
    public void retry(
            @PathVariable String actionInstanceId,
            @RequestHeader(value = "X-Action-Guard-Operator", required = false) String operator
    ) {
        actionCommandService.retry(actionInstanceId, operatorResolver.resolve(operator));
    }

    @PostMapping("/{actionInstanceId}/cancel")
    public void cancel(
            @PathVariable String actionInstanceId,
            @RequestHeader(value = "X-Action-Guard-Operator", required = false) String operator
    ) {
        actionCommandService.cancel(actionInstanceId, operatorResolver.resolve(operator));
    }

    @PostMapping("/{actionInstanceId}/skip")
    public void skip(
            @PathVariable String actionInstanceId,
            @RequestHeader(value = "X-Action-Guard-Operator", required = false) String operator
    ) {
        actionCommandService.skip(actionInstanceId, operatorResolver.resolve(operator));
    }

    @PostMapping("/{actionInstanceId}/compensate")
    public void compensate(
            @PathVariable String actionInstanceId,
            @RequestHeader(value = "X-Action-Guard-Operator", required = false) String operator
    ) {
        actionCommandService.compensate(actionInstanceId, operatorResolver.resolve(operator));
    }
}
