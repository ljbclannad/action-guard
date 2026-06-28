package io.github.actionguard.starter.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "action.guard")
public class ActionGuardProperties {

    private List<String> definitionLocations = List.of("classpath*:actions/*.yml", "classpath*:actions/*.yaml");
    private int publishRetryMaxAttempts = 1;
    private boolean metricsEnabled = true;
    private ActionGuardRecoveryProperties recovery = new ActionGuardRecoveryProperties();

    public List<String> getDefinitionLocations() {
        return definitionLocations;
    }

    public void setDefinitionLocations(List<String> definitionLocations) {
        this.definitionLocations = definitionLocations;
    }

    public int getPublishRetryMaxAttempts() {
        return publishRetryMaxAttempts;
    }

    public void setPublishRetryMaxAttempts(int publishRetryMaxAttempts) {
        this.publishRetryMaxAttempts = publishRetryMaxAttempts;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }

    public ActionGuardRecoveryProperties getRecovery() {
        return recovery;
    }

    public void setRecovery(ActionGuardRecoveryProperties recovery) {
        this.recovery = recovery;
    }
}
