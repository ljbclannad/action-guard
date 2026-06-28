package io.github.actionguard.starter;

import io.github.actionguard.core.runtime.ActionOutboxRecoveryService;
import io.github.actionguard.core.runtime.ActionCompensationService;
import io.github.actionguard.core.runtime.ActionStuckDetectionService;
import org.springframework.context.SmartLifecycle;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
        executorService.scheduleWithFixedDelay(
                this::runRecoveryCycle,
                delayMillis,
                delayMillis,
                TimeUnit.MILLISECONDS
        );
    }

    private void runRecoveryCycle() {
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
