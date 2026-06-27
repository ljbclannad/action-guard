package io.github.actionguard.core.runtime;

import io.github.actionguard.api.runtime.ActionAlertEvent;
import io.github.actionguard.api.spi.ActionAlertPublisher;
import io.github.actionguard.api.spi.ActionMetricsRecorder;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.model.ActionStepStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ActionObservabilityServiceTest {

    @Test
    void shouldPublishRetryExhaustedAlertAndMetric() {
        CapturingActionAlertPublisher alertPublisher = new CapturingActionAlertPublisher();
        CapturingActionMetricsRecorder metricsRecorder = new CapturingActionMetricsRecorder();
        ActionObservabilityService service = new ActionObservabilityService(
                Optional.of(alertPublisher),
                Optional.of(metricsRecorder),
                Clock.fixed(Instant.parse("2026-06-27T08:00:00Z"), ZoneOffset.UTC)
        );

        service.retryExhausted(
                new ActionInstance("act-1", "order-cancel-flow", "order:1", ActionStatus.FAILED, 0, 1, Map.of(), "E1", "failed", 0, Instant.now(), Instant.now()),
                new ActionStepInstance("step-1", "act-1", 0, "send-sms", "SMS", "notify.user", ActionStepStatus.FAILED, 3, Map.of(), "E1", "failed", 0, Instant.now(), Instant.now()),
                "E1",
                "failed"
        );

        assertThat(alertPublisher.events).hasSize(1);
        assertThat(alertPublisher.events.get(0).type().name()).isEqualTo("RETRIES_EXHAUSTED");
        assertThat(metricsRecorder.counters)
                .containsEntry("action.guard.retry.exhausted|{actionName=order-cancel-flow, stepType=SMS}", 1L)
                .containsEntry("action.guard.alert.published|{actionName=order-cancel-flow, stepType=SMS}", 1L);
    }

    @Test
    void shouldRecordStepAndActionSuccessMetrics() {
        CapturingActionMetricsRecorder metricsRecorder = new CapturingActionMetricsRecorder();
        ActionObservabilityService service = new ActionObservabilityService(
                Optional.empty(),
                Optional.of(metricsRecorder),
                Clock.fixed(Instant.parse("2026-06-27T08:00:00Z"), ZoneOffset.UTC)
        );
        ActionInstance actionInstance = new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.SUCCESS, 1, 1, Map.of(),
                null, null, 0, Instant.now(), Instant.now()
        );
        ActionStepInstance stepInstance = new ActionStepInstance(
                "step-1", "act-1", 0, "send-sms", "SMS", "notify.user", ActionStepStatus.SUCCESS, 1, Map.of(),
                null, null, 0, Instant.now(), Instant.now()
        );

        service.stepSucceeded(actionInstance, stepInstance);
        service.actionSucceeded(actionInstance, stepInstance);

        assertThat(metricsRecorder.counters)
                .containsEntry("action.guard.step.succeeded|{actionName=order-cancel-flow, result=success, stepType=SMS}", 1L)
                .containsEntry("action.guard.action.succeeded|{actionName=order-cancel-flow, result=success, stepType=SMS}", 1L);
    }

    @Test
    void shouldRecordStepTimeoutAndActionFailedMetrics() {
        CapturingActionMetricsRecorder metricsRecorder = new CapturingActionMetricsRecorder();
        ActionObservabilityService service = new ActionObservabilityService(
                Optional.empty(),
                Optional.of(metricsRecorder),
                Clock.fixed(Instant.parse("2026-06-27T08:00:00Z"), ZoneOffset.UTC)
        );
        ActionInstance actionInstance = new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.FAILED, 0, 1, Map.of(),
                "STEP_TIMEOUT", "timed out", 0, Instant.now(), Instant.now()
        );
        ActionStepInstance stepInstance = new ActionStepInstance(
                "step-1", "act-1", 0, "send-sms", "SMS", "notify.user", ActionStepStatus.FAILED, 1, Map.of(),
                "STEP_TIMEOUT", "timed out", 0, Instant.now(), Instant.now()
        );

        service.stepTimedOut(actionInstance, stepInstance);
        service.stepFailed(actionInstance, stepInstance, "STEP_TIMEOUT");
        service.actionFailed(actionInstance, stepInstance, "STEP_TIMEOUT");

        assertThat(metricsRecorder.counters)
                .containsEntry("action.guard.step.timed_out|{actionName=order-cancel-flow, result=timeout, stepType=SMS}", 1L)
                .containsEntry("action.guard.step.failed|{actionName=order-cancel-flow, errorCode=STEP_TIMEOUT, result=failed, stepType=SMS}", 1L)
                .containsEntry("action.guard.action.failed|{actionName=order-cancel-flow, errorCode=STEP_TIMEOUT, result=failed, stepType=SMS}", 1L);
    }

    @Test
    void shouldRecordCompensationAndGovernanceMetrics() {
        CapturingActionMetricsRecorder metricsRecorder = new CapturingActionMetricsRecorder();
        ActionObservabilityService service = new ActionObservabilityService(
                Optional.empty(),
                Optional.of(metricsRecorder),
                Clock.fixed(Instant.parse("2026-06-27T08:00:00Z"), ZoneOffset.UTC)
        );
        ActionInstance actionInstance = new ActionInstance(
                "act-1", "order-cancel-flow", "order:1", ActionStatus.COMPENSATED, 1, 1, Map.of(),
                null, null, 0, Instant.now(), Instant.now()
        );

        service.actionCompensated(actionInstance);
        service.governanceCommand("RETRY", "SUCCESS");

        assertThat(metricsRecorder.counters)
                .containsEntry("action.guard.action.compensated|{actionName=order-cancel-flow, result=compensated, stepType=unknown}", 1L)
                .containsEntry("action.guard.governance.command|{actionName=unknown, command=RETRY, result=SUCCESS, stepType=unknown}", 1L);
    }

    private static final class CapturingActionAlertPublisher implements ActionAlertPublisher {
        private final List<ActionAlertEvent> events = new ArrayList<>();

        @Override
        public void publish(ActionAlertEvent event) {
            events.add(event);
        }
    }

    private static final class CapturingActionMetricsRecorder implements ActionMetricsRecorder {
        private final java.util.LinkedHashMap<String, Long> counters = new java.util.LinkedHashMap<>();

        @Override
        public void increment(String metricName, Map<String, String> tags) {
            String key = metricName + "|" + new java.util.TreeMap<>(tags);
            counters.merge(key, 1L, Long::sum);
        }
    }
}
