package io.github.actionguard.starter.scheduler;

import io.github.actionguard.core.runtime.observability.ActionAlertOutboxRecoveryService;
import io.github.actionguard.starter.properties.ActionGuardAlertOutboxProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class ActionAlertOutboxRecoverySchedulerTest {

    @Test
    void shouldTriggerRecoveryWithConfiguredBatchAndClaimTimeout() {
        ActionAlertOutboxRecoveryService recoveryService = mock(ActionAlertOutboxRecoveryService.class);
        ActionGuardAlertOutboxProperties properties = properties();
        ActionAlertOutboxRecoveryScheduler scheduler = new ActionAlertOutboxRecoveryScheduler(recoveryService, properties);

        scheduler.runRecoveryCycle();

        verify(recoveryService).recoverDueAlerts(25, Duration.ofSeconds(45));
    }

    @Test
    void shouldIsolateFailureForLaterRecoveryCycles() {
        ActionAlertOutboxRecoveryService recoveryService = mock(ActionAlertOutboxRecoveryService.class);
        doThrow(new IllegalStateException("sender unavailable"))
                .when(recoveryService).recoverDueAlerts(anyInt(), any());
        ActionAlertOutboxRecoveryScheduler scheduler = new ActionAlertOutboxRecoveryScheduler(recoveryService, properties());

        scheduler.runRecoveryCycle();
        scheduler.runRecoveryCycle();

        verify(recoveryService, times(2)).recoverDueAlerts(25, Duration.ofSeconds(45));
    }

    private ActionGuardAlertOutboxProperties properties() {
        ActionGuardAlertOutboxProperties properties = new ActionGuardAlertOutboxProperties();
        properties.setBatchSize(25);
        properties.setClaimTimeout(Duration.ofSeconds(45));
        properties.setFixedDelay(Duration.ofSeconds(1));
        return properties;
    }
}
