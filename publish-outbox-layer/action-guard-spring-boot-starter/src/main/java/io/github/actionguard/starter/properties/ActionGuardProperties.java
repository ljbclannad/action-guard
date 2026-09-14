package io.github.actionguard.starter.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "action.guard")
public class ActionGuardProperties {

    private List<String> definitionLocations = List.of("classpath*:actions/*.yml", "classpath*:actions/*.yaml");
    private int publishRetryMaxAttempts = 1;
    private boolean metricsEnabled = true;
    private ActionGuardRecoveryProperties recovery = new ActionGuardRecoveryProperties();
    private ActionGuardAlertOutboxProperties alertOutbox = new ActionGuardAlertOutboxProperties();
    private Execution execution = new Execution();

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

    public ActionGuardAlertOutboxProperties getAlertOutbox() {
        return alertOutbox;
    }

    public void setAlertOutbox(ActionGuardAlertOutboxProperties alertOutbox) {
        this.alertOutbox = alertOutbox;
    }

    public Execution getExecution() {
        return execution;
    }

    public void setExecution(Execution execution) {
        this.execution = execution;
    }

    public static class Execution {
        /**
         * 未选择时不装配框架默认消息通道，仍允许业务自定义生产者。
         */
        private String transport;

        public String getTransport() {
            return transport;
        }

        public void setTransport(String transport) {
            this.transport = transport;
        }
    }
}
