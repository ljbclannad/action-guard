package io.github.actionguard.starter.properties;

import java.time.Duration;

public class ActionGuardRecoveryProperties {

    private boolean enabled;
    private int batchSize = 100;
    private Duration fixedDelay = Duration.ofSeconds(5);
    private Duration claimTimeout = Duration.ofSeconds(30);
    private Duration compensationTimeout = Duration.ofMinutes(1);
    private Duration stuckActionTimeout = Duration.ofMinutes(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getFixedDelay() {
        return fixedDelay;
    }

    public void setFixedDelay(Duration fixedDelay) {
        this.fixedDelay = fixedDelay;
    }

    public Duration getClaimTimeout() {
        return claimTimeout;
    }

    public void setClaimTimeout(Duration claimTimeout) {
        this.claimTimeout = claimTimeout;
    }

    public Duration getCompensationTimeout() {
        return compensationTimeout;
    }

    public void setCompensationTimeout(Duration compensationTimeout) {
        this.compensationTimeout = compensationTimeout;
    }

    public Duration getStuckActionTimeout() {
        return stuckActionTimeout;
    }

    public void setStuckActionTimeout(Duration stuckActionTimeout) {
        this.stuckActionTimeout = stuckActionTimeout;
    }
}
