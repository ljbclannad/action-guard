package io.github.actionguard.ops.api.service;

import io.github.actionguard.ops.api.model.ActionDetailView;
import io.github.actionguard.ops.api.model.ActionListItem;
import io.github.actionguard.ops.api.model.ActionQueryFilter;
import io.github.actionguard.ops.api.model.CompensationLogView;
import io.github.actionguard.ops.api.model.ConsumeDetailView;
import io.github.actionguard.ops.api.model.PageResult;
import io.github.actionguard.ops.api.model.StepDetailView;
import io.github.actionguard.ops.api.repository.ActionCompensationLogQueryRepository;
import io.github.actionguard.ops.api.repository.ActionOpsQueryRepository;

import java.util.List;

public class ActionQueryService {

    private final ActionOpsQueryRepository repository;
    private final ActionCompensationLogQueryRepository compensationLogQueryRepository;

    public ActionQueryService(
            ActionOpsQueryRepository repository,
            ActionCompensationLogQueryRepository compensationLogQueryRepository
    ) {
        this.repository = repository;
        this.compensationLogQueryRepository = compensationLogQueryRepository;
    }

    public PageResult<ActionListItem> list(ActionQueryFilter filter) {
        return repository.queryActions(filter);
    }

    public ActionDetailView detail(String actionInstanceId) {
        return repository.getActionDetail(actionInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Action not found: " + actionInstanceId));
    }

    public List<StepDetailView> steps(String actionInstanceId) {
        return repository.getSteps(actionInstanceId);
    }

    public List<ConsumeDetailView> consumes(String actionInstanceId) {
        return repository.getConsumes(actionInstanceId);
    }

    public List<CompensationLogView> compensations(String actionInstanceId) {
        return compensationLogQueryRepository.findByActionInstanceId(actionInstanceId);
    }
}
