package io.github.actionguard.core.runtime.observability;

import io.github.actionguard.api.runtime.ActionAlertEvent;
import io.github.actionguard.api.runtime.ActionAlertLevel;
import io.github.actionguard.api.runtime.ActionAlertType;
import io.github.actionguard.api.spi.ActionAlertPublisher;
import io.github.actionguard.api.spi.ActionMetricsRecorder;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionStepInstance;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.time.Duration;

public class ActionObservabilityService {

    private final Optional<ActionAlertPublisher> actionAlertPublisher;
    private final Optional<ActionMetricsRecorder> actionMetricsRecorder;
    private final Clock clock;

    public ActionObservabilityService(
            Optional<ActionAlertPublisher> actionAlertPublisher,
            Optional<ActionMetricsRecorder> actionMetricsRecorder,
            Clock clock
    ) {
        this.actionAlertPublisher = Objects.requireNonNull(actionAlertPublisher, "actionAlertPublisher must not be null");
        this.actionMetricsRecorder = Objects.requireNonNull(actionMetricsRecorder, "actionMetricsRecorder must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void retryExhausted(ActionInstance actionInstance, ActionStepInstance stepInstance, String errorCode, String errorMessage) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("bizKey", nullSafe(actionInstance.bizKey()));
        details.put("stepIndex", String.valueOf(stepInstance.stepIndex()));
        details.put("errorCode", nullSafe(errorCode));
        details.put("errorMessage", nullSafe(errorMessage));
        details.put("attemptCount", String.valueOf(stepInstance.attemptCount()));
        publishEvent(
                ActionAlertType.RETRIES_EXHAUSTED,
                ActionAlertLevel.HIGH,
                "action retries exhausted",
                errorMessage,
                actionInstance.actionName(),
                actionInstance.id(),
                stepInstance.stepName(),
                stepInstance.stepType(),
                details
        );
        increment("action.guard.retry.exhausted", actionInstance.actionName(), stepInstance.stepType());
    }

    public void compensationFailed(ActionInstance actionInstance, ActionStepInstance stepInstance, String resultMessage) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("bizKey", nullSafe(actionInstance.bizKey()));
        details.put("stepIndex", String.valueOf(stepInstance.stepIndex()));
        details.put("resultMessage", nullSafe(resultMessage));
        publishEvent(
                ActionAlertType.COMPENSATION_FAILED,
                ActionAlertLevel.HIGH,
                "action compensation failed",
                resultMessage,
                actionInstance.actionName(),
                actionInstance.id(),
                stepInstance.stepName(),
                stepInstance.stepType(),
                details
        );
        increment("action.guard.compensation.failed", actionInstance.actionName(), stepInstance.stepType());
    }

    public void consumeFailure(String consumerGroup, String actionInstanceId, String messageId, String reason) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("consumerGroup", nullSafe(consumerGroup));
        details.put("messageId", nullSafe(messageId));
        details.put("reason", nullSafe(reason));
        publishEvent(
                ActionAlertType.CONSUME_FAILURE,
                ActionAlertLevel.MEDIUM,
                "action consume failure",
                reason,
                null,
                actionInstanceId,
                null,
                null,
                details
        );
        increment("action.guard.consume.failed", "unknown", "unknown");
    }

    public void deadLetter(String consumerGroup, String actionInstanceId, String messageId, String reason) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("consumerGroup", nullSafe(consumerGroup));
        details.put("messageId", nullSafe(messageId));
        details.put("reason", nullSafe(reason));
        publishEvent(
                ActionAlertType.DEAD_LETTER,
                ActionAlertLevel.HIGH,
                "action dead letter",
                reason,
                null,
                actionInstanceId,
                null,
                null,
                details
        );
        increment("action.guard.dead.letter", "unknown", "unknown");
    }

    public void outboxPublishFailed(ActionOutbox outbox, int attemptedCount, String reason) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("outboxId", outbox.id());
        details.put("attemptedCount", String.valueOf(attemptedCount));
        details.put("reason", nullSafe(reason));
        publishEvent(
                ActionAlertType.OUTBOX_PUBLISH_FAILED,
                ActionAlertLevel.HIGH,
                "action outbox publish failed",
                reason,
                null,
                outbox.actionInstanceId(),
                null,
                outbox.topic(),
                details
        );
        increment("action.guard.outbox.publish.failed", "unknown", outbox.topic());
    }

    public void actionStuck(ActionInstance actionInstance, Duration timeout) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("bizKey", nullSafe(actionInstance.bizKey()));
        details.put("status", actionInstance.status().name());
        details.put("updatedAt", String.valueOf(actionInstance.updatedAt()));
        details.put("timeout", String.valueOf(timeout));
        publishEvent(
                ActionAlertType.ACTION_STUCK,
                ActionAlertLevel.MEDIUM,
                "action stuck",
                "action has not progressed within " + timeout,
                actionInstance.actionName(),
                actionInstance.id(),
                null,
                null,
                details
        );
        increment("action.guard.action.stuck", actionInstance.actionName(), "unknown");
    }

    public void stepSucceeded(ActionInstance actionInstance, ActionStepInstance stepInstance) {
        increment("action.guard.step.succeeded", Map.of(
                "actionName", nullSafe(actionInstance.actionName()),
                "stepType", nullSafe(stepInstance.stepType()),
                "result", "success"
        ));
    }

    public void stepFailed(ActionInstance actionInstance, ActionStepInstance stepInstance, String errorCode) {
        increment("action.guard.step.failed", Map.of(
                "actionName", nullSafe(actionInstance.actionName()),
                "stepType", nullSafe(stepInstance.stepType()),
                "result", "failed",
                "errorCode", nullSafe(errorCode)
        ));
    }

    public void stepTimedOut(ActionInstance actionInstance, ActionStepInstance stepInstance) {
        increment("action.guard.step.timed_out", Map.of(
                "actionName", nullSafe(actionInstance.actionName()),
                "stepType", nullSafe(stepInstance.stepType()),
                "result", "timeout"
        ));
    }

    public void actionSucceeded(ActionInstance actionInstance, ActionStepInstance stepInstance) {
        increment("action.guard.action.succeeded", Map.of(
                "actionName", nullSafe(actionInstance.actionName()),
                "stepType", nullSafe(stepInstance.stepType()),
                "result", "success"
        ));
    }

    public void actionFailed(ActionInstance actionInstance, ActionStepInstance stepInstance, String errorCode) {
        increment("action.guard.action.failed", Map.of(
                "actionName", nullSafe(actionInstance.actionName()),
                "stepType", nullSafe(stepInstance.stepType()),
                "result", "failed",
                "errorCode", nullSafe(errorCode)
        ));
    }

    public void actionCompensated(ActionInstance actionInstance) {
        increment("action.guard.action.compensated", Map.of(
                "actionName", nullSafe(actionInstance.actionName()),
                "stepType", "unknown",
                "result", "compensated"
        ));
    }

    public void governanceCommand(String command, String result) {
        increment("action.guard.governance.command", Map.of(
                "actionName", "unknown",
                "stepType", "unknown",
                "command", nullSafe(command),
                "result", nullSafe(result)
        ));
    }

    private void publishEvent(
            ActionAlertType type,
            ActionAlertLevel level,
            String title,
            String message,
            String actionName,
            String actionInstanceId,
            String stepName,
            String stepType,
            Map<String, String> details
    ) {
        actionAlertPublisher.ifPresent(publisher -> publisher.publish(new ActionAlertEvent(
                type,
                level,
                title,
                message,
                actionName,
                actionInstanceId,
                stepName,
                stepType,
                clock.instant(),
                Map.copyOf(details)
        )));
        increment("action.guard.alert.published", actionName, stepType);
    }

    private void increment(String metricName, String actionName, String stepType) {
        increment(metricName, Map.of(
                "actionName", nullSafe(actionName),
                "stepType", nullSafe(stepType)
        ));
    }

    private void increment(String metricName, Map<String, String> tags) {
        actionMetricsRecorder.ifPresent(recorder -> recorder.increment(metricName, tags));
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
