package io.github.actionguard.core.runtime.observability;

import io.github.actionguard.api.runtime.ActionAlertEvent;
import io.github.actionguard.api.runtime.ActionAlertLevel;
import io.github.actionguard.api.runtime.ActionAlertType;
import io.github.actionguard.api.spi.ActionAlertPublisher;
import io.github.actionguard.api.spi.ActionMetricsRecorder;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.runtime.state.ActionTransitionResult;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ActionObservabilityService {

    private static final System.Logger LOGGER = System.getLogger(ActionObservabilityService.class.getName());
    private final ActionAlertPublisher actionAlertPublisher;
    private final ActionAlertOutboxRecorder actionAlertOutboxRecorder;
    private final ActionMetricsRecorder actionMetricsRecorder;
    private final Clock clock;

    /**
     * 保留 Optional 作为兼容构造器入口；可选通道会在构造阶段立即解包，不进入运行时通知路径。
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public ActionObservabilityService(
            Optional<ActionAlertPublisher> actionAlertPublisher,
            Optional<ActionMetricsRecorder> actionMetricsRecorder,
            Clock clock
    ) {
        this.actionAlertPublisher = Objects.requireNonNull(actionAlertPublisher, "actionAlertPublisher must not be null")
                .orElse(null);
        this.actionAlertOutboxRecorder = null;
        this.actionMetricsRecorder = Objects.requireNonNull(actionMetricsRecorder, "actionMetricsRecorder must not be null")
                .orElse(null);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 可靠告警路径：告警在当前业务事务内入队，指标仍保持提交后 best-effort。
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public ActionObservabilityService(
            ActionAlertOutboxRecorder actionAlertOutboxRecorder,
            Optional<ActionMetricsRecorder> actionMetricsRecorder,
            Clock clock
    ) {
        this.actionAlertPublisher = null;
        this.actionAlertOutboxRecorder = Objects.requireNonNull(actionAlertOutboxRecorder, "actionAlertOutboxRecorder must not be null");
        this.actionMetricsRecorder = Objects.requireNonNull(actionMetricsRecorder, "actionMetricsRecorder must not be null")
                .orElse(null);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void retryExhausted(ActionInstance actionInstance, ActionStepInstance stepInstance, String errorCode, String errorMessage) {
        // 告警事件和指标在同一个入口里发出，避免调用方到处散落“先记指标、再发告警”的重复逻辑。
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
                null,
                details
        );
        increment("action.guard.outbox.publish.failed", "unknown", "unknown");
    }

    /**
     * 在 Outbox 成功进入投递终态后发送一次当前进程内的尽力而为通知。
     *
     * <p>该通知不具备持久化、重试或外部送达回执语义。</p>
     */
    public void outboxDead(ActionOutbox outbox, int maxDeliveryAttempts, String reason) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("outboxId", outbox.id());
        details.put("dispatchId", outbox.dispatchId());
        details.put("topic", outbox.topic());
        details.put("status", ActionOutboxStatus.DEAD.name());
        details.put("deliveryAttemptCount", String.valueOf(outbox.deliveryAttemptCount()));
        details.put("maxDeliveryAttempts", String.valueOf(maxDeliveryAttempts));
        details.put("reason", nullSafe(reason));
        publishEvent(
                ActionAlertType.OUTBOX_DEAD,
                ActionAlertLevel.HIGH,
                "action outbox delivery dead",
                reason,
                null,
                outbox.actionInstanceId(),
                null,
                null,
                details
        );
        increment("action.guard.outbox.delivery.dead", Map.of(
                "actionName", "unknown",
                "stepType", "unknown",
                "reason", "delivery_exhausted"
        ));
    }

    /** 记录当前进程在恢复扫描中成功完成投递的 Outbox 数量。 */
    public void outboxRecoverySucceeded(int recoveredCount) {
        for (int index = 0; index < recoveredCount; index++) {
            increment("action.guard.outbox.recovery.succeeded", "unknown", "unknown");
        }
    }

    /**
     * 记录恢复调度阶段异常。
     *
     * <p>异常详情只写入日志，避免异常消息成为高基数指标标签。</p>
     */
    public void recoveryPhaseFailed(String phase, RuntimeException exception) {
        LOGGER.log(System.Logger.Level.WARNING, "Action Guard recovery phase failed: " + phase, exception);
        increment("action.guard.recovery.phase.failed", Map.of(
                "actionName", "unknown",
                "stepType", "unknown",
                "phase", nullSafe(phase)
        ));
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

    public void actionTransition(ActionTransitionResult transitionResult) {
        increment("action.guard.action.transition", Map.of(
                "actionName", nullSafe(transitionResult.actionInstance().actionName()),
                "stepType", "unknown",
                "fromStatus", transitionResult.fromStatus().name(),
                "toStatus", transitionResult.toStatus().name(),
                "event", transitionResult.event().name()
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
        // 告警发布是可选能力，缺少 publisher 不应影响主链路执行，所以这里统一通过 Optional 降级。
        ActionAlertEvent event = new ActionAlertEvent(
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
        );
        if (actionAlertOutboxRecorder != null) {
            // 不能包裹在 afterCommit 中：写入失败必须参与当前业务事务并触发回滚。
            actionAlertOutboxRecorder.record(event);
        } else {
            // 仅为直接构造旧服务的接入方保留过渡性 best-effort 行为。
            afterCommitOrNow(() -> {
                if (actionAlertPublisher != null) {
                    actionAlertPublisher.publish(event);
                }
            });
        }
        increment("action.guard.alert.published", actionName, stepType);
    }

    private void increment(String metricName, String actionName, String stepType) {
        increment(metricName, Map.of(
                "actionName", nullSafe(actionName),
                "stepType", nullSafe(stepType)
        ));
    }

    private void increment(String metricName, Map<String, String> tags) {
        // 指标同样允许缺省，以便框架在没有接入外部监控系统时仍能独立运行。
        afterCommitOrNow(() -> {
            if (actionMetricsRecorder != null) {
                actionMetricsRecorder.increment(metricName, tags);
            }
        });
    }

    private void afterCommitOrNow(Runnable notification) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        notification.run();
                    } catch (RuntimeException ex) {
                        // 结果已经提交，监控通道故障不能阻断后续投递或伪装成执行失败。
                        LOGGER.log(System.Logger.Level.WARNING, "事务提交后的告警或指标发送失败", ex);
                    }
                }
            });
        } else {
            try {
                notification.run();
            } catch (RuntimeException ex) {
                // 无事务调用同样不能让可选观测出口反向破坏已经完成的状态转换。
                LOGGER.log(System.Logger.Level.WARNING, "告警或指标发送失败", ex);
            }
        }
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
