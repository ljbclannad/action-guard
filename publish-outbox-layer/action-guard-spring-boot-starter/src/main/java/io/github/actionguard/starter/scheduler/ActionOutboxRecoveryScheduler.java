package io.github.actionguard.starter.scheduler;

import io.github.actionguard.core.runtime.compensation.ActionCompensationService;
import io.github.actionguard.core.runtime.recovery.ActionOutboxRecoveryService;
import io.github.actionguard.core.runtime.recovery.ActionStuckDetectionService;
import io.github.actionguard.starter.properties.ActionGuardRecoveryProperties;
import org.springframework.context.SmartLifecycle;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Starter 层提供的恢复调度器。
 *
 * <p>它是 Action Guard 在 Spring Boot 应用中的后台守护入口：应用启动后，按固定周期串行触发
 * outbox 恢复、补偿恢复和 stuck action 检测，把 core 层的恢复能力接入实际运行环境。
 *
 * <p>这个类本身不实现恢复算法，只负责生命周期管理和调度编排。之所以放在 starter 层，
 * 是因为它依赖 Spring 的 {@link SmartLifecycle}，属于“把 runtime 能力接到应用里”的基础设施代码。
 */
public class ActionOutboxRecoveryScheduler implements SmartLifecycle {

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "action-guard-outbox-recovery");
        thread.setDaemon(true);
        return thread;
    });

    private final ActionOutboxRecoveryService recoveryService;
    private final Optional<ActionCompensationService> compensationService;
    private final Optional<ActionStuckDetectionService> stuckDetectionService;
    private final ActionGuardRecoveryProperties properties;
    private volatile boolean running;

    public ActionOutboxRecoveryScheduler(
            ActionOutboxRecoveryService recoveryService,
            Optional<ActionCompensationService> compensationService,
            Optional<ActionStuckDetectionService> stuckDetectionService,
            ActionGuardRecoveryProperties properties
    ) {
        this.recoveryService = Objects.requireNonNull(recoveryService, "recoveryService must not be null");
        this.compensationService = Objects.requireNonNull(compensationService, "compensationService must not be null");
        this.stuckDetectionService = Objects.requireNonNull(stuckDetectionService, "stuckDetectionService must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public void start() {
        if (running || !properties.isEnabled()) {
            return;
        }
        running = true;
        long delayMillis = Math.max(1000L, properties.getFixedDelay().toMillis());
        // recovery 统一串到一个单线程调度器里，优先保证“不会并发打架”，而不是追求扫描吞吐量。
        executorService.scheduleWithFixedDelay(
                this::runRecoveryCycle,
                delayMillis,
                delayMillis,
                TimeUnit.MILLISECONDS
        );
    }

    private void runRecoveryCycle() {
        // 一个周期内顺序处理 outbox 恢复、补偿恢复和 stuck 检测。
        // 这样链路清晰，出问题时也更容易从日志判断卡在哪个恢复阶段。
        recoveryService.recoverDueOutboxes(properties.getBatchSize(), properties.getClaimTimeout());
        compensationService.ifPresent(service -> service.recoverInterruptedCompensations(
                properties.getBatchSize(),
                properties.getCompensationTimeout()
        ));
        stuckDetectionService.ifPresent(service -> service.detectStuckActions(
                properties.getBatchSize(),
                properties.getStuckActionTimeout()
        ));
    }

    @Override
    public void stop() {
        running = false;
        executorService.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
