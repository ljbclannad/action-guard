package io.github.actionguard.ops.api.service;

import io.github.actionguard.core.model.ActionConsumeStatus;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.model.ActionStepStatus;
import io.github.actionguard.core.model.ActionTransitionLog;
import io.github.actionguard.core.repository.ActionTransitionLogRepository;
import io.github.actionguard.core.runtime.state.ActionTransitionEvent;
import io.github.actionguard.ops.api.model.ActionDetailView;
import io.github.actionguard.ops.api.model.CompensationLogView;
import io.github.actionguard.ops.api.model.ConsumeDetailView;
import io.github.actionguard.ops.api.model.StepDetailView;
import io.github.actionguard.ops.api.repository.ActionCompensationLogQueryRepository;
import io.github.actionguard.ops.api.repository.ActionOpsQueryRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ActionQueryServiceTest {

    @Test
    void shouldAssembleActionTimelineAcrossRuntimeAndGovernanceSources() {
        ActionOpsQueryRepository queryRepository = mock(ActionOpsQueryRepository.class);
        ActionCompensationLogQueryRepository compensationRepository = mock(ActionCompensationLogQueryRepository.class);
        ActionTransitionLogRepository transitionLogRepository = mock(ActionTransitionLogRepository.class);
        ActionQueryService service = new ActionQueryService(queryRepository, compensationRepository, transitionLogRepository);

        given(queryRepository.getActionDetail("act-1")).willReturn(Optional.of(new ActionDetailView(
                "act-1",
                "order-cancel-flow",
                "order:1",
                ActionStatus.IGNORED,
                0,
                1,
                null,
                null,
                Instant.parse("2026-06-26T12:00:00Z"),
                Instant.parse("2026-06-26T12:01:00Z"),
                List.of(new StepDetailView(
                        0,
                        "send-user-sms",
                        "SMS",
                        "notify.user",
                        ActionStepStatus.SUCCESS,
                        1,
                        null,
                        null,
                        Instant.parse("2026-06-26T12:00:10Z"),
                        Instant.parse("2026-06-26T12:00:20Z")
                )),
                List.of(new ConsumeDetailView(
                        "ACTION_EXECUTE:outbox-1",
                        "action-guard-demo",
                        ActionConsumeStatus.ACKED,
                        1,
                        null,
                        Instant.parse("2026-06-26T12:00:05Z"),
                        Instant.parse("2026-06-26T12:00:05Z"),
                        Instant.parse("2026-06-26T12:00:05Z")
                )),
                List.of()
        )));
        given(compensationRepository.findByActionInstanceId("act-1")).willReturn(List.of(
                new CompensationLogView(
                        "batch-1",
                        0,
                        "send-user-sms",
                        "SMS",
                        "SUCCESS",
                        "SmsCompensator",
                        "ok",
                        Instant.parse("2026-06-26T12:00:40Z")
                )
        ));
        given(transitionLogRepository.findByActionInstanceId("act-1")).willReturn(List.of(
                new ActionTransitionLog(
                        "transition-1",
                        "act-1",
                        ActionTransitionEvent.MANUAL_CANCEL_REQUESTED,
                        ActionStatus.DISPATCHING,
                        ActionStatus.IGNORED,
                        0,
                        "send-user-sms",
                        "SMS",
                        "anonymous",
                        null,
                        null,
                        Instant.parse("2026-06-26T12:00:30Z")
                )
        ));

        var timeline = service.timeline("act-1");

        assertThat(timeline).hasSize(6);
        assertThat(timeline).extracting(item -> item.category())
                .containsExactly("ACTION", "CONSUME", "STEP", "STEP", "TRANSITION", "COMPENSATION");
        assertThat(timeline.get(4).fromStatus()).isEqualTo("DISPATCHING");
        assertThat(timeline.get(4).toStatus()).isEqualTo("IGNORED");
        assertThat(timeline.get(4).stepType()).isEqualTo("SMS");
    }

    @Test
    void shouldAttachTimelineToActionDetail() {
        ActionOpsQueryRepository queryRepository = mock(ActionOpsQueryRepository.class);
        ActionCompensationLogQueryRepository compensationRepository = mock(ActionCompensationLogQueryRepository.class);
        ActionTransitionLogRepository transitionLogRepository = mock(ActionTransitionLogRepository.class);
        ActionQueryService service = new ActionQueryService(queryRepository, compensationRepository, transitionLogRepository);

        given(queryRepository.getActionDetail("act-2")).willReturn(Optional.of(new ActionDetailView(
                "act-2",
                "demo-flow",
                "biz-2",
                ActionStatus.SUCCESS,
                1,
                1,
                null,
                null,
                Instant.parse("2026-06-26T12:00:00Z"),
                Instant.parse("2026-06-26T12:01:00Z"),
                List.of(),
                List.of(),
                List.of()
        )));
        given(compensationRepository.findByActionInstanceId("act-2")).willReturn(List.of());
        given(transitionLogRepository.findByActionInstanceId("act-2")).willReturn(List.of(
                new ActionTransitionLog(
                        "transition-2",
                        "act-2",
                        ActionTransitionEvent.STEP_SUCCEEDED,
                        ActionStatus.NEW,
                        ActionStatus.SUCCESS,
                        0,
                        "step-1",
                        "SMS",
                        null,
                        null,
                        null,
                        Instant.parse("2026-06-26T12:00:30Z")
                )
        ));

        var detail = service.detail("act-2");

        assertThat(detail.timeline()).hasSize(2);
        assertThat(detail.timeline().get(1).category()).isEqualTo("TRANSITION");
    }
}
