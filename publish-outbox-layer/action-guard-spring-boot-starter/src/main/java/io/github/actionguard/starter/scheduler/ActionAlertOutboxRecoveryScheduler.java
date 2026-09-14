package io.github.actionguard.starter.scheduler;

import io.github.actionguard.core.runtime.observability.ActionAlertOutboxRecoveryService;
import io.github.actionguard.starter.properties.ActionGuardAlertOutboxProperties;
import org.springframework.context.SmartLifecycle;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 使用独立线程恢复告警投递，避免慢告警通道阻塞执行 Outbox 恢复。
 */
public class ActionAlertOutboxRecoveryScheduler implements SmartLifecycle {

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "action-guard-alert-outbox-recovery");
        thread.setDaemon(true);
        return thread;
    });
    private final ActionAlertOutboxRecoveryService recoveryService;
    private final ActionGuardAlertOutboxProperties properties;
    private volatile boolean running;

    public ActionAlertOutboxRecoveryScheduler(
            ActionAlertOutboxRecoveryService recoveryService,
            ActionGuardAlertOutboxProperties properties
    ) {
        this.recoveryService = Objects.requireNonNull(recoveryService, "recoveryService must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public void start() {
        if (running || !properties.isEnabled()) {
            return;
        }
        running = true;
        long delayMillis = Math.max(1000L, properties.getFixedDelay().toMillis());
        executorService.scheduleWithFixedDelay(this::runRecoveryCycle, delayMillis, delayMillis, TimeUnit.MILLISECONDS);
    }

    void runRecoveryCycle() {
        try {
            recoveryService.recoverDueAlerts(properties.getBatchSize(), properties.getClaimTimeout());
        } catch (RuntimeException exception) {
            System.getLogger(ActionAlertOutboxRecoveryScheduler.class.getName()).log(
                    System.Logger.Level.WARNING, "Action Guard alert outbox recovery failed", exception);
        }
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
