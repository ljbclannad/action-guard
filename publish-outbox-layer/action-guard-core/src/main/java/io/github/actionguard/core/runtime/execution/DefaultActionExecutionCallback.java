package io.github.actionguard.core.runtime.execution;

import io.github.actionguard.api.runtime.ActionExecutionMessage;
import io.github.actionguard.api.runtime.ActionRetryAction;
import io.github.actionguard.api.runtime.ActionRetryContext;
import io.github.actionguard.api.runtime.ActionStepContext;
import io.github.actionguard.api.runtime.StepExecutionResult;
import io.github.actionguard.api.spi.ActionRetryPolicy;
import io.github.actionguard.api.spi.ActionStepHandler;
import io.github.actionguard.api.definition.ActionStepDefinition;
import io.github.actionguard.core.model.ActionInstance;
import io.github.actionguard.core.model.ActionOutbox;
import io.github.actionguard.core.model.ActionOutboxStatus;
import io.github.actionguard.core.model.ActionStatus;
import io.github.actionguard.core.model.ActionStepInstance;
import io.github.actionguard.core.model.ActionStepStatus;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.InMemoryActionTransitionLogRepository;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.repository.ActionStepInstanceRepository;
import io.github.actionguard.core.repository.ActionTransitionLogRepository;
import io.github.actionguard.core.runtime.definition.ActionDefinitionRegistry;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import io.github.actionguard.core.runtime.registry.StepHandlerRegistry;
import io.github.actionguard.core.runtime.state.ActionTransitionExecution;
import io.github.actionguard.core.runtime.state.ActionTransitionEvent;
import io.github.actionguard.core.runtime.state.ActionTransitionService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Action 执行回调的运行时协调器。
 *
 * <p>它位于 {@code consumer -> callback -> handler} 链路：根据 {@link ActionExecutionMessage} 定位当前
 * Action 和 Step，调用对应的 {@link ActionStepHandler}，再根据执行结果推进状态机、记录迁移日志，并在需要时
 * 调度后续 Outbox。
 *
 * <p>事务边界分为两段：Handler 在 {@code NOT_SUPPORTED} 范围执行，不占用结果事务；Handler 返回后，
 * Step、Action、迁移日志和后续 Outbox 在短 {@code REQUIRES_NEW} 事务内一起写入。该事务提交并释放资源后，
 * 才通过 {@link ActionOutboxDispatcher} 投递 Outbox。数据库事务无法撤销已发生的外部 Handler 副作用，
 * Handler 仍必须保证幂等。
 */
public class DefaultActionExecutionCallback implements ActionExecutionCallback {

    private final ActionInstanceRepository actionInstanceRepository;
    private final ActionStepInstanceRepository actionStepInstanceRepository;
    private final ActionDefinitionRegistry actionDefinitionRegistry;
    private final StepHandlerRegistry stepHandlerRegistry;
    private final ActionRetryPolicy actionRetryPolicy;
    private final ActionOutboxRepository actionOutboxRepository;
    private final ActionTransitionLogRepository actionTransitionLogRepository;
    private final ActionObservabilityService actionObservabilityService;
    private final ActionTransitionService actionTransitionService;
    private final ActionExecutionRuntimeService actionExecutionRuntimeService;
    private final Clock clock;
    private final ActionOutboxDispatcher outboxDispatcher;
    private final TransactionOperations resultTransactions;
    private final TransactionOperations outsideTransactions;

    public DefaultActionExecutionCallback(
            ActionInstanceRepository actionInstanceRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionDefinitionRegistry actionDefinitionRegistry,
            StepHandlerRegistry stepHandlerRegistry,
            ActionRetryPolicy actionRetryPolicy,
            ActionOutboxRepository actionOutboxRepository,
            Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer,
            Clock clock
    ) {
        this(
                actionInstanceRepository,
                actionStepInstanceRepository,
                actionDefinitionRegistry,
                stepHandlerRegistry,
                actionRetryPolicy,
                actionOutboxRepository,
                new InMemoryActionTransitionLogRepository(),
                actionExecutionMessageProducer,
                new ActionObservabilityService(Optional.empty(), Optional.empty(), clock),
                clock
        );
    }

    public DefaultActionExecutionCallback(
            ActionInstanceRepository actionInstanceRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionDefinitionRegistry actionDefinitionRegistry,
            StepHandlerRegistry stepHandlerRegistry,
            ActionRetryPolicy actionRetryPolicy,
            ActionOutboxRepository actionOutboxRepository,
            Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer,
            ActionObservabilityService actionObservabilityService,
            Clock clock
    ) {
        this(actionInstanceRepository, actionStepInstanceRepository, actionDefinitionRegistry, stepHandlerRegistry,
                actionRetryPolicy, actionOutboxRepository, new InMemoryActionTransitionLogRepository(), actionExecutionMessageProducer,
                actionObservabilityService, clock, Optional.empty());
    }

    public DefaultActionExecutionCallback(
            ActionInstanceRepository actionInstanceRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionDefinitionRegistry actionDefinitionRegistry,
            StepHandlerRegistry stepHandlerRegistry,
            ActionRetryPolicy actionRetryPolicy,
            ActionOutboxRepository actionOutboxRepository,
            ActionTransitionLogRepository actionTransitionLogRepository,
            Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer,
            ActionObservabilityService actionObservabilityService,
            Clock clock
    ) {
        this(
                actionInstanceRepository,
                actionStepInstanceRepository,
                actionDefinitionRegistry,
                stepHandlerRegistry,
                actionRetryPolicy,
                actionOutboxRepository,
                actionTransitionLogRepository,
                actionExecutionMessageProducer,
                actionObservabilityService,
                clock,
                Optional.empty()
        );
    }

    /** 数据库仓储需传入管理同一数据源的事务管理器；无管理器的构造方式仅用于内存运行。 */
    public DefaultActionExecutionCallback(
            ActionInstanceRepository actionInstanceRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionDefinitionRegistry actionDefinitionRegistry,
            StepHandlerRegistry stepHandlerRegistry,
            ActionRetryPolicy actionRetryPolicy,
            ActionOutboxRepository actionOutboxRepository,
            ActionTransitionLogRepository actionTransitionLogRepository,
            Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer,
            ActionObservabilityService actionObservabilityService,
            Clock clock,
            Optional<PlatformTransactionManager> transactionManager
    ) {
        this.actionInstanceRepository = Objects.requireNonNull(actionInstanceRepository, "actionInstanceRepository must not be null");
        this.actionStepInstanceRepository = Objects.requireNonNull(actionStepInstanceRepository, "actionStepInstanceRepository must not be null");
        this.actionDefinitionRegistry = Objects.requireNonNull(actionDefinitionRegistry, "actionDefinitionRegistry must not be null");
        this.stepHandlerRegistry = Objects.requireNonNull(stepHandlerRegistry, "stepHandlerRegistry must not be null");
        this.actionRetryPolicy = Objects.requireNonNull(actionRetryPolicy, "actionRetryPolicy must not be null");
        this.actionOutboxRepository = Objects.requireNonNull(actionOutboxRepository, "actionOutboxRepository must not be null");
        this.actionTransitionLogRepository = Objects.requireNonNull(actionTransitionLogRepository, "actionTransitionLogRepository must not be null");
        this.actionObservabilityService = Objects.requireNonNull(actionObservabilityService, "actionObservabilityService must not be null");
        this.actionTransitionService = new ActionTransitionService(
                this.actionInstanceRepository,
                this.actionTransitionLogRepository,
                this.actionObservabilityService
        );
        this.actionExecutionRuntimeService = new ActionExecutionRuntimeService(
                this.actionStepInstanceRepository,
                this.actionTransitionService
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.outboxDispatcher = new ActionOutboxDispatcher(actionOutboxRepository, actionExecutionMessageProducer, actionObservabilityService, clock);
        this.resultTransactions = transactionManager
                .<TransactionOperations>map(manager -> transactionTemplate(manager, TransactionDefinition.PROPAGATION_REQUIRES_NEW))
                .orElseGet(TransactionOperations::withoutTransaction);
        this.outsideTransactions = transactionManager
                .<TransactionOperations>map(manager -> transactionTemplate(manager, TransactionDefinition.PROPAGATION_NOT_SUPPORTED))
                .orElseGet(TransactionOperations::withoutTransaction);
    }

    private static TransactionTemplate transactionTemplate(PlatformTransactionManager manager, int propagation) {
        TransactionTemplate template = new TransactionTemplate(manager);
        template.setPropagationBehavior(propagation);
        return template;
    }

    public DefaultActionExecutionCallback(
            ActionInstanceRepository actionInstanceRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionDefinitionRegistry actionDefinitionRegistry,
            StepHandlerRegistry stepHandlerRegistry,
            ActionRetryPolicy actionRetryPolicy,
            ActionOutboxRepository actionOutboxRepository,
            ActionTransitionLogRepository actionTransitionLogRepository,
            Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer,
            Clock clock
    ) {
        this(
                actionInstanceRepository,
                actionStepInstanceRepository,
                actionDefinitionRegistry,
                stepHandlerRegistry,
                actionRetryPolicy,
                actionOutboxRepository,
                actionTransitionLogRepository,
                actionExecutionMessageProducer,
                new ActionObservabilityService(Optional.empty(), Optional.empty(), clock),
                clock
        );
    }

    @Override
    public void execute(ActionExecutionMessage message) {
        // 挂起调用方已有事务后再读取并执行 Handler，避免外部调用长时间占用结果事务的数据库连接。
        outsideTransactions.executeWithoutResult(status -> executeOutsideTransaction(message));
    }

    private void executeOutsideTransaction(ActionExecutionMessage message) {
        ActionInstance actionInstance = actionInstanceRepository.findById(message.actionInstanceId())
                .orElseThrow(() -> new IllegalArgumentException("ActionInstance not found: " + message.actionInstanceId()));
        // 终态动作天然幂等，重复投递的执行消息在这里直接短路，避免重复推进状态。
        if (actionInstance.status().isTerminal()) {
            return;
        }
        List<ActionStepInstance> stepInstances = actionStepInstanceRepository.findByActionInstanceId(message.actionInstanceId());
        if (actionInstance.currentStepIndex() >= stepInstances.size()) {
            if (actionInstance.status().isTerminal()) {
                return;
            }
            throw new IllegalStateException("No executable step for actionInstanceId: " + message.actionInstanceId());
        }

        ActionStepInstance currentStep = stepInstances.get(actionInstance.currentStepIndex());
        // 当前 step 已成功时直接返回，防止消费重复消息时再次执行同一个 handler。
        if (currentStep.status() == ActionStepStatus.SUCCESS) {
            return;
        }
        ActionStepDefinition stepDefinition = resolveStepDefinition(actionInstance, currentStep);
        ActionStepHandler handler = stepHandlerRegistry.getRequired(currentStep.stepType());
        Instant startedAt = clock.instant();
        StepExecutionResult result;
        try {
            result = handler.execute(new ActionStepContext(
                    actionInstance.actionName(),
                    actionInstance.bizKey(),
                    currentStep.stepName(),
                    currentStep.stepType(),
                    currentStep.target(),
                    actionInstance.attributes(),
                    currentStep.payload()
            ));
        } catch (RuntimeException ex) {
            result = StepExecutionResult.failed("STEP_EXECUTION_EXCEPTION", normalizedThrowableMessage(ex));
        }
        Instant completedAt = clock.instant();
        // 超时在 Handler 返回后按耗时判定，不会主动中断正在执行的外部调用。
        StepExecutionResult effectiveResult = applyTimeoutIfExceeded(result, stepDefinition, startedAt, completedAt);

        // 结果事务内统一写入 Step、Action、迁移日志和待投递 Outbox；任一写入失败则不产生下一步投递。
        ActionOutbox scheduledOutbox = resultTransactions.execute(status -> effectiveResult.success()
                ? handleStepSuccess(actionInstance, currentStep)
                : handleStepFailure(actionInstance, currentStep, stepDefinition, effectiveResult));
        // 返回时结果事务已提交并释放连接；投递失败不会回滚已提交结果，Outbox 可由恢复任务继续处理。
        if (scheduledOutbox != null) {
            outboxDispatcher.dispatch(scheduledOutbox, 1);
        }
    }

    private ActionOutbox handleStepSuccess(ActionInstance actionInstance, ActionStepInstance currentStep) {
        Instant now = clock.instant();
        int nextStepIndex = currentStep.stepIndex() + 1;
        ActionExecutionProgress progress = actionExecutionRuntimeService.completeStepSuccess(
                actionInstance,
                currentStep,
                nextStepIndex,
                now
        );
        actionObservabilityService.stepSucceeded(actionInstance, progress.stepInstance());
        ActionTransitionExecution transitionExecution = progress.transitionExecution();
        ActionInstance advanced = transitionExecution.transitionResult().actionInstance();
        ActionStatus nextStatus = advanced.status();
        if (nextStatus == ActionStatus.SUCCESS) {
            actionObservabilityService.actionSucceeded(advanced, progress.stepInstance());
        }
        if (nextStatus == ActionStatus.DISPATCHING) {
            // 只有在还有后续 step 时才继续投递下一条执行消息，第一版始终保持严格串行。
            return scheduleOutbox(advanced.id(), now, false);
        }
        return null;
    }

    private ActionOutbox handleStepFailure(
            ActionInstance actionInstance,
            ActionStepInstance currentStep,
            ActionStepDefinition stepDefinition,
            StepExecutionResult result
    ) {
        Instant now = clock.instant();
        String errorMessage = normalizedErrorMessage(result);
        // 失败先落到 step 实例，后续 retry / fail-fast / compensate 的决策都基于这次持久化结果。
        ActionStepInstance failedStep = actionExecutionRuntimeService.persistFailedStep(
                currentStep,
                result,
                errorMessage,
                now
        );
        int maxRetryCount = stepDefinition.maxRetryCount() == null ? Integer.MAX_VALUE : stepDefinition.maxRetryCount();
        ActionRetryAction retryAction = actionRetryPolicy.decide(
                new StepExecutionException(result.errorCode(), errorMessage),
                new ActionRetryContext(failedStep.attemptCount(), maxRetryCount, true)
        );
        if ("STEP_TIMEOUT".equals(result.errorCode())) {
            actionObservabilityService.stepTimedOut(actionInstance, failedStep);
        }
        actionObservabilityService.stepFailed(actionInstance, failedStep, result.errorCode());
        if (retryAction == ActionRetryAction.IMMEDIATE_RETRY || retryAction == ActionRetryAction.DELAY_RETRY) {
            // 重试不创建新的 Action：同一 Outbox 记录重新调度，但会生成新的 dispatchId 代表新的逻辑投递。
            ActionTransitionExecution retryTransition = actionExecutionRuntimeService.transitionFailure(
                    actionInstance,
                    failedStep,
                    result,
                    errorMessage,
                    ActionTransitionEvent.STEP_FAILED_RETRYABLE,
                    now
            );
            ActionInstance retrying = retryTransition.transitionResult().actionInstance();
            return scheduleOutbox(retrying.id(), now.plusMillis(resolvedBackoffMillis(stepDefinition, retryAction)), true);
        }
        // 走到这里说明当前策略已经放弃继续执行，action 进入 FAILED，等待人工治理或补偿链路接管。
        ActionTransitionExecution terminalFailureTransition = actionExecutionRuntimeService.transitionFailure(
                actionInstance,
                failedStep,
                result,
                errorMessage,
                ActionTransitionEvent.STEP_FAILED_TERMINAL,
                now
        );
        actionObservabilityService.actionFailed(
                terminalFailureTransition.transitionResult().actionInstance(),
                failedStep,
                result.errorCode()
        );
        actionObservabilityService.retryExhausted(actionInstance, failedStep, result.errorCode(), errorMessage);
        return null;
    }

    private String normalizedErrorMessage(StepExecutionResult result) {
        return result.errorMessage() == null || result.errorMessage().isBlank()
                ? "step execution failed"
                : result.errorMessage();
    }

    private String normalizedThrowableMessage(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName()
                : ex.getMessage();
    }

    private ActionOutbox scheduleOutbox(String actionInstanceId, Instant availableAt, boolean incrementAttemptCount) {
        ActionOutbox outbox = actionOutboxRepository.findByActionInstanceId(actionInstanceId)
                .orElseThrow(() -> new IllegalStateException("Outbox not found for actionInstanceId: " + actionInstanceId));
        // 下一步推进和业务重试是新的逻辑投递，必须生成新的 dispatchId；传输失败后的重发由 dispatcher 保留原值。
        return actionOutboxRepository.save(new ActionOutbox(
                outbox.id(),
                outbox.actionInstanceId(),
                outbox.topic(),
                UUID.randomUUID().toString(),
                ActionOutboxStatus.NEW,
                availableAt,
                incrementAttemptCount ? outbox.attemptCount() + 1 : outbox.attemptCount(),
                outbox.version(),
                outbox.createdAt(),
                clock.instant()
        ));
    }

    private ActionStepDefinition resolveStepDefinition(ActionInstance actionInstance, ActionStepInstance currentStep) {
        ActionStepDefinition stepDefinition = actionDefinitionRegistry.getRequired(actionInstance.actionName())
                .steps()
                .get(currentStep.stepIndex());
        if (!stepDefinition.stepType().equals(currentStep.stepType())) {
            throw new IllegalStateException("Action definition step type mismatch: " + actionInstance.actionName() + "/" + currentStep.stepName());
        }
        return stepDefinition;
    }

    private StepExecutionResult applyTimeoutIfExceeded(
            StepExecutionResult result,
            ActionStepDefinition stepDefinition,
            Instant startedAt,
            Instant completedAt
    ) {
        if (result == null) {
            throw new IllegalStateException("step execution result must not be null");
        }
        if (stepDefinition.timeoutMillis() == null) {
            return result;
        }
        long elapsedMillis = Math.max(0, completedAt.toEpochMilli() - startedAt.toEpochMilli());
        if (elapsedMillis <= stepDefinition.timeoutMillis()) {
            return result;
        }
        return StepExecutionResult.failed(
                "STEP_TIMEOUT",
                "step execution timed out after " + elapsedMillis + " ms"
        );
    }

    private long resolvedBackoffMillis(ActionStepDefinition stepDefinition, ActionRetryAction retryAction) {
        if (retryAction == ActionRetryAction.DELAY_RETRY) {
            return stepDefinition.retryBackoffMillis() == null ? 0L : stepDefinition.retryBackoffMillis();
        }
        return stepDefinition.retryBackoffMillis() == null ? 0L : stepDefinition.retryBackoffMillis();
    }

    private static final class StepExecutionException extends RuntimeException {
        private final String errorCode;

        private StepExecutionException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        @SuppressWarnings("unused")
        private String errorCode() {
            return errorCode;
        }
    }
}
