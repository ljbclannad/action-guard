package io.github.actionguard.ops.api.controller;

import io.github.actionguard.ops.api.model.ActionCommandRequest;
import io.github.actionguard.ops.api.security.ActionOpsPrincipal;
import io.github.actionguard.ops.api.security.ActionOpsRequestAttributes;
import io.github.actionguard.ops.api.service.ActionCommandService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/actions")
public class ActionCommandController {

    private final ActionCommandService actionCommandService;

    public ActionCommandController(ActionCommandService actionCommandService) {
        this.actionCommandService = actionCommandService;
    }

    @PostMapping("/{actionInstanceId}/retry")
    public void retry(
            @PathVariable String actionInstanceId,
            @RequestAttribute(ActionOpsRequestAttributes.PRINCIPAL) ActionOpsPrincipal principal,
            @RequestBody ActionCommandRequest request
    ) {
        actionCommandService.retry(actionInstanceId, principal.operatorId(), requiredReason(request));
    }

    @PostMapping("/{actionInstanceId}/cancel")
    public void cancel(
            @PathVariable String actionInstanceId,
            @RequestAttribute(ActionOpsRequestAttributes.PRINCIPAL) ActionOpsPrincipal principal,
            @RequestBody ActionCommandRequest request
    ) {
        actionCommandService.cancel(actionInstanceId, principal.operatorId(), requiredReason(request));
    }

    @PostMapping("/{actionInstanceId}/skip")
    public void skip(
            @PathVariable String actionInstanceId,
            @RequestAttribute(ActionOpsRequestAttributes.PRINCIPAL) ActionOpsPrincipal principal,
            @RequestBody ActionCommandRequest request
    ) {
        actionCommandService.skip(actionInstanceId, principal.operatorId(), requiredReason(request));
    }

    @PostMapping("/{actionInstanceId}/compensate")
    public void compensate(
            @PathVariable String actionInstanceId,
            @RequestAttribute(ActionOpsRequestAttributes.PRINCIPAL) ActionOpsPrincipal principal,
            @RequestBody ActionCommandRequest request
    ) {
        actionCommandService.compensate(actionInstanceId, principal.operatorId(), requiredReason(request));
    }

    private String requiredReason(ActionCommandRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reason 不能为空");
        }
        return request.reason();
    }
}
