package io.github.actionguard.starter.scheduler;

import io.github.actionguard.api.spi.ActionMetricsRecorder;
import io.github.actionguard.core.runtime.compensation.ActionCompensationService;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import io.github.actionguard.core.runtime.recovery.ActionOutboxRecoveryService;
import io.github.actionguard.core.runtime.recovery.ActionStuckDetectionService;
import io.github.actionguard.starter.properties.ActionGuardRecoveryProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionOutboxRecoverySchedulerTest {

    @Test
    void shouldRecordRecoveredOutboxes() {
        ActionOutboxRecoveryService outboxRecovery = mock(ActionOutboxRecoveryService.class);
        when(outboxRecovery.recoverDueOutboxes(anyInt(), any())).thenReturn(2);
        CapturingMetricsRecorder metrics = new CapturingMetricsRecorder();
        ActionOutboxRecoveryScheduler scheduler = scheduler(outboxRecovery, Optional.empty(), Optional.empty(), metrics);

        scheduler.runRecoveryCycle();

        assertThat(metrics.counters).containsEntry(
                "action.guard.outbox.recovery.succeeded|{actionName=unknown, stepType=unknown}", 2L);
    }

    @Test
    void shouldContinueLaterPhasesWhenOutboxRecoveryFails() {
        ActionOutboxRecoveryService outboxRecovery = mock(ActionOutboxRecoveryService.class);
        ActionCompensationService compensation = mock(ActionCompensationService.class);
        ActionStuckDetectionService stuckDetection = mock(ActionStuckDetectionService.class);
        doThrow(new IllegalStateException("outbox database failed"))
                .when(outboxRecovery).recoverDueOutboxes(anyInt(), any());
        CapturingMetricsRecorder metrics = new CapturingMetricsRecorder();
        ActionOutboxRecoveryScheduler scheduler = scheduler(
                outboxRecovery, Optional.of(compensation), Optional.of(stuckDetection), metrics);

        scheduler.runRecoveryCycle();

        verify(compensation).recoverInterruptedCompensations(anyInt(), any());
        verify(stuckDetection).detectStuckActions(anyInt(), any());
        assertThat(metrics.counters).containsEntry(
                "action.guard.recovery.phase.failed|{actionName=unknown, phase=outbox, stepType=unknown}", 1L);
    }

    @Test
    void shouldContinueStuckDetectionWhenCompensationFails() {
        ActionOutboxRecoveryService outboxRecovery = mock(ActionOutboxRecoveryService.class);
        ActionCompensationService compensation = mock(ActionCompensationService.class);
        ActionStuckDetectionService stuckDetection = mock(ActionStuckDetectionService.class);
        doThrow(new IllegalStateException("compensation failed"))
                .when(compensation).recoverInterruptedCompensations(anyInt(), any());
        ActionOutboxRecoveryScheduler scheduler = scheduler(
                outboxRecovery, Optional.of(compensation), Optional.of(stuckDetection), new CapturingMetricsRecorder());

        scheduler.runRecoveryCycle();

        verify(stuckDetection).detectStuckActions(anyInt(), any());
    }

    @Test
    void shouldIsolateStuckFailureAndRunLaterCycles() {
        ActionOutboxRecoveryService outboxRecovery = mock(ActionOutboxRecoveryService.class);
        ActionStuckDetectionService stuckDetection = mock(ActionStuckDetectionService.class);
        doThrow(new IllegalStateException("stuck detection failed"))
                .when(stuckDetection).detectStuckActions(anyInt(), any());
        CapturingMetricsRecorder metrics = new CapturingMetricsRecorder();
        ActionOutboxRecoveryScheduler scheduler = scheduler(
                outboxRecovery, Optional.empty(), Optional.of(stuckDetection), metrics);

        scheduler.runRecoveryCycle();
        scheduler.runRecoveryCycle();

        verify(stuckDetection, times(2)).detectStuckActions(anyInt(), any());
        assertThat(metrics.counters).containsEntry(
                "action.guard.recovery.phase.failed|{actionName=unknown, phase=stuck, stepType=unknown}", 2L);
    }

    private ActionOutboxRecoveryScheduler scheduler(
            ActionOutboxRecoveryService outboxRecovery,
            Optional<ActionCompensationService> compensation,
            Optional<ActionStuckDetectionService> stuckDetection,
            CapturingMetricsRecorder metrics
    ) {
        ActionGuardRecoveryProperties properties = new ActionGuardRecoveryProperties();
        properties.setEnabled(true);
        properties.setFixedDelay(Duration.ofSeconds(1));
        return new ActionOutboxRecoveryScheduler(
                outboxRecovery,
                compensation,
                stuckDetection,
                properties,
                new ActionObservabilityService(Optional.empty(), Optional.of(metrics), Clock.systemUTC())
        );
    }

    private static final class CapturingMetricsRecorder implements ActionMetricsRecorder {
        private final Map<String, Long> counters = new LinkedHashMap<>();

        @Override
        public void increment(String metricName, Map<String, String> tags) {
            counters.merge(metricName + "|" + new TreeMap<>(tags), 1L, Long::sum);
        }
    }
}
