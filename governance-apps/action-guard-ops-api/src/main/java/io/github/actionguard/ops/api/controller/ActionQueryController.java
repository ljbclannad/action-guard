package io.github.actionguard.ops.api.controller;

import io.github.actionguard.ops.api.model.ActionDetailView;
import io.github.actionguard.ops.api.model.ActionListItem;
import io.github.actionguard.ops.api.model.ActionQueryFilter;
import io.github.actionguard.ops.api.model.CompensationLogView;
import io.github.actionguard.ops.api.model.ConsumeDetailView;
import io.github.actionguard.ops.api.model.PageResult;
import io.github.actionguard.ops.api.model.StepDetailView;
import io.github.actionguard.ops.api.service.ActionQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
