package io.github.actionguard.ops.api.controller;

import io.github.actionguard.ops.api.model.AuditLogQueryFilter;
import io.github.actionguard.ops.api.model.AuditLogView;
import io.github.actionguard.ops.api.model.PageResult;
import io.github.actionguard.ops.api.service.ActionAuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/audit-logs")
public class ActionAuditController {

    private final ActionAuditService actionAuditService;

    public ActionAuditController(ActionAuditService actionAuditService) {
        this.actionAuditService = actionAuditService;
    }

    @GetMapping
    public PageResult<AuditLogView> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String actionInstanceId,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo
    ) {
        return actionAuditService.query(new AuditLogQueryFilter(page, size, actionInstanceId, operationType, operator, createdFrom, createdTo));
    }
}
