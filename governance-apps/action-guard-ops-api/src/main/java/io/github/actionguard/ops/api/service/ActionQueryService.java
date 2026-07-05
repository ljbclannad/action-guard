package io.github.actionguard.ops.api.service;

import io.github.actionguard.ops.api.model.ActionDetailView;
import io.github.actionguard.ops.api.model.ActionListItem;
import io.github.actionguard.ops.api.model.ActionQueryFilter;
import io.github.actionguard.ops.api.model.ActionTimelineEventView;
import io.github.actionguard.ops.api.model.CompensationLogView;
import io.github.actionguard.ops.api.model.ConsumeDetailView;
import io.github.actionguard.ops.api.model.PageResult;
import io.github.actionguard.ops.api.model.StepDetailView;
import io.github.actionguard.core.model.ActionTransitionLog;
import io.github.actionguard.core.repository.ActionTransitionLogRepository;
import io.github.actionguard.ops.api.repository.ActionCompensationLogQueryRepository;
import io.github.actionguard.ops.api.repository.ActionOpsQueryRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ActionQueryService {

    private final ActionOpsQueryRepository repository;
    private final ActionCompensationLogQueryRepository compensationLogQueryRepository;
    private final ActionTransitionLogRepository actionTransitionLogRepository;

    public ActionQueryService(
            ActionOpsQueryRepository repository,
            ActionCompensationLogQueryRepository compensationLogQueryRepository,
            ActionTransitionLogRepository actionTransitionLogRepository
    ) {
        this.repository = repository;
        this.compensationLogQueryRepository = compensationLogQueryRepository;
        this.actionTransitionLogRepository = actionTransitionLogRepository;
    }

    public PageResult<ActionListItem> list(ActionQueryFilter filter) {
        return repository.queryActions(filter);
    }

    public ActionDetailView detail(String actionInstanceId) {
        ActionDetailView detail = repository.getActionDetail(actionInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Action not found: " + actionInstanceId));
        return new ActionDetailView(
                detail.actionInstanceId(),
                detail.actionName(),
                detail.bizKey(),
                detail.status(),
                detail.currentStepIndex(),
                detail.totalStepCount(),
                detail.lastErrorCode(),
                detail.lastErrorMessage(),
                detail.createdAt(),
                detail.updatedAt(),
                detail.steps(),
                detail.consumes(),
                buildTimeline(detail)
        );
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

    public List<ActionTimelineEventView> timeline(String actionInstanceId) {
        ActionDetailView detail = detail(actionInstanceId);
        return detail.timeline();
    }

    private List<ActionTimelineEventView> buildTimeline(ActionDetailView detail) {
        List<ActionTimelineEventView> events = new ArrayList<>();
        events.add(new ActionTimelineEventView(
                detail.createdAt(),
                "ACTION",
                "Action Published",
                detail.actionName() + " / " + detail.bizKey(),
                null,
                "NEW",
                null,
                null
        ));

        for (StepDetailView step : detail.steps()) {
            events.add(new ActionTimelineEventView(
                    step.createdAt(),
                    "STEP",
                    "Step Registered",
                    step.status().name() + " / attempt=" + step.attemptCount(),
                    null,
                    step.status().name(),
                    step.stepName(),
                    step.stepType()
            ));
            if (!step.updatedAt().equals(step.createdAt())) {
                events.add(new ActionTimelineEventView(
                        step.updatedAt(),
                        "STEP",
                        "Step Updated",
                        timelineSummary(step.status().name(), step.attemptCount(), step.lastErrorCode(), step.lastErrorMessage()),
                        null,
                        step.status().name(),
                        step.stepName(),
                        step.stepType()
                ));
            }
        }

        for (ConsumeDetailView consume : detail.consumes()) {
            events.add(new ActionTimelineEventView(
                    consume.firstReceivedAt(),
                    "CONSUME",
                    "Message Received",
                    consume.consumeStatus().name() + " / consumerGroup=" + consume.consumerGroup(),
                    null,
                    consume.consumeStatus().name(),
                    null,
                    consume.consumerGroup()
            ));
            if (!consume.updatedAt().equals(consume.firstReceivedAt())) {
                events.add(new ActionTimelineEventView(
                        consume.updatedAt(),
                        "CONSUME",
                        "Message Consumption Updated",
                        timelineSummary(consume.consumeStatus().name(), consume.attemptCount(), null, consume.lastErrorMessage()),
                        null,
                        consume.consumeStatus().name(),
                        null,
                        consume.consumerGroup()
                ));
            }
        }

        for (CompensationLogView compensation : compensations(detail.actionInstanceId())) {
            events.add(new ActionTimelineEventView(
                    compensation.createdAt(),
                    "COMPENSATION",
                    "Compensation Logged",
                    timelineSummary(compensation.compensationStatus(), null, compensation.compensatorName(), compensation.resultMessage()),
                    null,
                    compensation.compensationStatus(),
                    compensation.stepName(),
                    compensation.stepType()
            ));
        }

        for (ActionTransitionLog transitionLog : actionTransitionLogRepository.findByActionInstanceId(detail.actionInstanceId())) {
            events.add(new ActionTimelineEventView(
                    transitionLog.createdAt(),
                    "TRANSITION",
                    "Action Transition",
                    transitionTimelineSummary(
                            transitionLog.event().name(),
                            transitionLog.stepIndex(),
                            transitionLog.errorCode(),
                            transitionLog.errorMessage() != null
                                    ? transitionLog.errorMessage()
                                    : transitionLog.operator()
                    ),
                    transitionLog.fromStatus().name(),
                    transitionLog.toStatus().name(),
                    transitionLog.stepName(),
                    transitionLog.stepType() != null ? transitionLog.stepType() : transitionLog.event().name()
            ));
        }

        return events.stream()
                .sorted(Comparator.comparing(ActionTimelineEventView::occurredAt))
                .toList();
    }

    private String timelineSummary(String status, Integer attemptCount, String code, String message) {
        List<String> parts = new ArrayList<>();
        parts.add(status);
        if (attemptCount != null) {
            parts.add("attempt=" + attemptCount);
        }
        if (code != null && !code.isBlank()) {
            parts.add("code=" + code);
        }
        if (message != null && !message.isBlank()) {
            parts.add(message);
        }
        return String.join(" / ", parts);
    }

    private String transitionTimelineSummary(String status, Integer stepIndex, String code, String message) {
        List<String> parts = new ArrayList<>();
        parts.add(status);
        if (stepIndex != null) {
            parts.add("stepIndex=" + stepIndex);
        }
        if (code != null && !code.isBlank()) {
            parts.add("code=" + code);
        }
        if (message != null && !message.isBlank()) {
            parts.add(message);
        }
        return String.join(" / ", parts);
    }
}
