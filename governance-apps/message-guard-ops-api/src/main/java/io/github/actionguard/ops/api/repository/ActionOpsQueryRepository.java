package io.github.actionguard.ops.api.repository;

import io.github.actionguard.ops.api.model.ActionDetailView;
import io.github.actionguard.ops.api.model.ActionListItem;
import io.github.actionguard.ops.api.model.ActionQueryFilter;
import io.github.actionguard.ops.api.model.ConsumeDetailView;
import io.github.actionguard.ops.api.model.PageResult;
import io.github.actionguard.ops.api.model.StepDetailView;

import java.util.List;
import java.util.Optional;

public interface ActionOpsQueryRepository {

    PageResult<ActionListItem> queryActions(ActionQueryFilter filter);

    Optional<ActionDetailView> getActionDetail(String actionInstanceId);

    List<StepDetailView> getSteps(String actionInstanceId);

    List<ConsumeDetailView> getConsumes(String actionInstanceId);
}
