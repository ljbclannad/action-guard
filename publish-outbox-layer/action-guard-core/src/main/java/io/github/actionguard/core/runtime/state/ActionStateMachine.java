package io.github.actionguard.core.runtime.state;

import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Action 状态迁移器。
 *
 * <p>它处在 {@code runtime / governance -> persistence} 这段链路的中间位置：执行回调、
 * 补偿服务、人工治理命令先把“发生了什么”表达成 {@link ActionTransitionEvent}，再把当前
 * {@link ActionInstance} 和必要上下文交给这里；这里负责校验事件在当前状态下是否合法，
 * 解析出目标状态，并生成一份结构化的 {@link ActionTransitionResult}。
 *
 * <p>因此它不关心 MQ 投递、补偿实现、Controller 入参或仓储细节。它只做一件事：
 * 把动作生命周期中的状态推进规则收敛成单一事实来源，避免不同调用点各自手写
 * {@code if(status == ...)} 分支，导致语义漂移。
 */
public final class ActionStateMachine {

    private static final Map<ActionStatus, Set<ActionStatus>> ALLOWED_TRANSITIONS = allowedTransitions();
    private static final Map<ActionCommand, Set<ActionStatus>> ALLOWED_COMMAND_STATUSES = allowedCommandStatuses();
    private static final Map<ActionTransitionEvent, Set<ActionStatus>> ALLOWED_EVENT_STATUSES = allowedEventStatuses();

    private ActionStateMachine() {
    }

    public static boolean canTransition(ActionStatus currentStatus, ActionStatus nextStatus) {
        if (currentStatus == null || nextStatus == null) {
            return false;
        }
        if (currentStatus == nextStatus) {
            return true;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of()).contains(nextStatus);
    }

    public static void assertTransition(ActionStatus currentStatus, ActionStatus nextStatus) {
        if (!canTransition(currentStatus, nextStatus)) {
            throw new IllegalStateException("Action status transition is not allowed: " + currentStatus + " -> " + nextStatus);
        }
    }

    public static boolean canExecute(ActionStatus status, ActionCommand command) {
        if (status == null || command == null) {
            return false;
        }
        return ALLOWED_COMMAND_STATUSES.getOrDefault(command, Set.of()).contains(status);
    }

    public static void assertCommandAllowed(ActionStatus status, ActionCommand command) {
        if (!canExecute(status, command)) {
            throw new IllegalStateException(commandLabel(command) + " is not allowed for status: " + status);
        }
    }

    public static boolean canApply(ActionStatus status, ActionTransitionEvent event) {
        if (status == null || event == null) {
            return false;
        }
        return ALLOWED_EVENT_STATUSES.getOrDefault(event, Set.of()).contains(status);
    }

    public static void assertEventAllowed(ActionStatus status, ActionTransitionEvent event) {
        if (!canApply(status, event)) {
            throw new IllegalStateException("Action transition event is not allowed for status: " + status + " / " + event);
        }
    }

    public static ActionTransitionResult apply(
            ActionInstance instance,
            ActionTransitionEvent event,
            ActionTransitionContext context
    ) {
        Objects.requireNonNull(instance, "instance must not be null");
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(context.occurredAt(), "occurredAt must not be null");

        assertEventAllowed(instance.status(), event);
        ActionStatus nextStatus = resolveNextStatus(instance, event, context);
        assertTransition(instance.status(), nextStatus);

        ActionInstance transitioned = new ActionInstance(
                instance.id(),
                instance.actionName(),
                instance.bizKey(),
                nextStatus,
                context.currentStepIndex(),
                instance.totalStepCount(),
                instance.attributes(),
                context.lastErrorCode(),
                context.lastErrorMessage(),
                instance.version(),
                instance.createdAt(),
                context.occurredAt()
        );
        return new ActionTransitionResult(instance.status(), nextStatus, event, transitioned);
    }

    private static ActionStatus resolveNextStatus(
            ActionInstance instance,
            ActionTransitionEvent event,
            ActionTransitionContext context
    ) {
        return switch (event) {
            case STEP_SUCCEEDED, MANUAL_SKIP_REQUESTED ->
                    context.currentStepIndex() >= instance.totalStepCount() ? ActionStatus.SUCCESS : ActionStatus.DISPATCHING;
            case STEP_FAILED_RETRYABLE -> ActionStatus.RETRYING;
            case STEP_FAILED_TERMINAL -> ActionStatus.FAILED;
            case MANUAL_CANCEL_REQUESTED -> ActionStatus.IGNORED;
            case COMPENSATION_STARTED -> ActionStatus.COMPENSATING;
            case COMPENSATION_SUCCEEDED -> ActionStatus.COMPENSATED;
            case COMPENSATION_FAILED -> ActionStatus.DEAD;
        };
    }

    private static Map<ActionStatus, Set<ActionStatus>> allowedTransitions() {
        Map<ActionStatus, Set<ActionStatus>> transitions = new EnumMap<>(ActionStatus.class);
        transitions.put(ActionStatus.NEW, EnumSet.of(ActionStatus.DISPATCHING, ActionStatus.SUCCESS, ActionStatus.RETRYING, ActionStatus.FAILED, ActionStatus.IGNORED));
        transitions.put(ActionStatus.DISPATCHING, EnumSet.of(ActionStatus.RETRYING, ActionStatus.SUCCESS, ActionStatus.FAILED, ActionStatus.IGNORED));
        transitions.put(ActionStatus.RETRYING, EnumSet.of(ActionStatus.DISPATCHING, ActionStatus.SUCCESS, ActionStatus.FAILED, ActionStatus.IGNORED));
        transitions.put(ActionStatus.FAILED, EnumSet.of(ActionStatus.COMPENSATING));
        transitions.put(ActionStatus.DEAD, EnumSet.of(ActionStatus.COMPENSATING));
        transitions.put(ActionStatus.COMPENSATING, EnumSet.of(ActionStatus.COMPENSATED, ActionStatus.DEAD));
        transitions.put(ActionStatus.SUCCESS, EnumSet.noneOf(ActionStatus.class));
        transitions.put(ActionStatus.COMPENSATED, EnumSet.noneOf(ActionStatus.class));
        transitions.put(ActionStatus.IGNORED, EnumSet.noneOf(ActionStatus.class));
        return Map.copyOf(transitions);
    }

    private static Map<ActionCommand, Set<ActionStatus>> allowedCommandStatuses() {
        Map<ActionCommand, Set<ActionStatus>> commands = new EnumMap<>(ActionCommand.class);
        commands.put(ActionCommand.RETRY, EnumSet.of(ActionStatus.FAILED, ActionStatus.RETRYING));
        commands.put(ActionCommand.SKIP, EnumSet.of(ActionStatus.DISPATCHING, ActionStatus.RETRYING));
        commands.put(ActionCommand.CANCEL, EnumSet.of(ActionStatus.NEW, ActionStatus.DISPATCHING, ActionStatus.RETRYING));
        commands.put(ActionCommand.COMPENSATE, EnumSet.of(ActionStatus.FAILED, ActionStatus.DEAD));
        return Map.copyOf(commands);
    }

    private static Map<ActionTransitionEvent, Set<ActionStatus>> allowedEventStatuses() {
        Map<ActionTransitionEvent, Set<ActionStatus>> events = new EnumMap<>(ActionTransitionEvent.class);
        events.put(ActionTransitionEvent.STEP_SUCCEEDED, EnumSet.of(ActionStatus.NEW, ActionStatus.DISPATCHING, ActionStatus.RETRYING));
        events.put(ActionTransitionEvent.STEP_FAILED_RETRYABLE, EnumSet.of(ActionStatus.NEW, ActionStatus.DISPATCHING, ActionStatus.RETRYING));
        events.put(ActionTransitionEvent.STEP_FAILED_TERMINAL, EnumSet.of(ActionStatus.NEW, ActionStatus.DISPATCHING, ActionStatus.RETRYING));
        events.put(ActionTransitionEvent.MANUAL_SKIP_REQUESTED, EnumSet.of(ActionStatus.DISPATCHING, ActionStatus.RETRYING));
        events.put(ActionTransitionEvent.MANUAL_CANCEL_REQUESTED, EnumSet.of(ActionStatus.NEW, ActionStatus.DISPATCHING, ActionStatus.RETRYING));
        events.put(ActionTransitionEvent.COMPENSATION_STARTED, EnumSet.of(ActionStatus.FAILED, ActionStatus.DEAD, ActionStatus.COMPENSATING));
        events.put(ActionTransitionEvent.COMPENSATION_SUCCEEDED, EnumSet.of(ActionStatus.COMPENSATING));
        events.put(ActionTransitionEvent.COMPENSATION_FAILED, EnumSet.of(ActionStatus.COMPENSATING));
        return Map.copyOf(events);
    }

    private static String commandLabel(ActionCommand command) {
        return switch (command) {
            case RETRY -> "Retry";
            case SKIP -> "Skip";
            case CANCEL -> "Cancel";
            case COMPENSATE -> "Compensate";
        };
    }
}
