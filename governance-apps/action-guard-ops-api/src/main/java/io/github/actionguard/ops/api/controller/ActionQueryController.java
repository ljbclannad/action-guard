package io.github.actionguard.ops.api.controller;

import io.github.actionguard.ops.api.model.*;
import io.github.actionguard.ops.api.service.ActionQueryService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/actions")
public class ActionQueryController {

    private final ActionQueryService actionQueryService;

    public ActionQueryController(ActionQueryService actionQueryService) {
        this.actionQueryService = actionQueryService;
    }

    @GetMapping
    public PageResult<ActionListItem> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String actionName,
            @RequestParam(required = false) String bizKey,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo
    ) {
        return actionQueryService.list(new ActionQueryFilter(page, size, actionName, bizKey, status, createdFrom, createdTo));
    }

    @GetMapping("/{actionInstanceId}")
    public ActionDetailView detail(@PathVariable String actionInstanceId) {
        return actionQueryService.detail(actionInstanceId);
    }

    @GetMapping("/{actionInstanceId}/steps")
    public List<StepDetailView> steps(@PathVariable String actionInstanceId) {
        return actionQueryService.steps(actionInstanceId);
    }

    @GetMapping("/{actionInstanceId}/consumes")
    public List<ConsumeDetailView> consumes(@PathVariable String actionInstanceId) {
        return actionQueryService.consumes(actionInstanceId);
    }

    @GetMapping("/{actionInstanceId}/compensations")
    public List<CompensationLogView> compensations(@PathVariable String actionInstanceId) {
        return actionQueryService.compensations(actionInstanceId);
    }

    @GetMapping("/{actionInstanceId}/outboxes")
    public List<ActionOutboxView> outboxes(@PathVariable String actionInstanceId) {
        return actionQueryService.outboxes(actionInstanceId);
    }

    @GetMapping("/{actionInstanceId}/alert-outboxes")
    public List<ActionAlertOutboxView> alertOutboxes(@PathVariable String actionInstanceId) {
        return actionQueryService.alertOutboxes(actionInstanceId);
    }

    @GetMapping("/{actionInstanceId}/timeline")
    public List<ActionTimelineEventView> timeline(@PathVariable String actionInstanceId) {
        return actionQueryService.timeline(actionInstanceId);
    }
}
