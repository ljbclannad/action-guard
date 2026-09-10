package io.github.actionguard.adapter.rocketmq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "action.guard.rocketmq")
public class ActionGuardRocketMqProperties {

    private String nameServer;
    private String topic = "action-guard-execute";
    private String producerGroup = "action-guard-rocketmq-producer";
    private String consumerGroup = "action-guard-rocketmq-consumer";
    private int maxRedeliveries = 1;
    private Duration sendTimeout = Duration.ofSeconds(5);
    private boolean consumerEnabled = true;
    private boolean startupProbeEnabled;
    private String startupProbeTopic = "action-guard-health-probe";
    private Duration startupProbeTimeout = Duration.ofSeconds(10);

    public String getNameServer() {
        return nameServer;
    }

    public void setNameServer(String nameServer) {
        this.nameServer = nameServer;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public int getMaxRedeliveries() {
        return maxRedeliveries;
    }

    public void setMaxRedeliveries(int maxRedeliveries) {
        this.maxRedeliveries = maxRedeliveries;
    }

    public Duration getSendTimeout() {
        return sendTimeout;
    }

    public void setSendTimeout(Duration sendTimeout) {
        this.sendTimeout = sendTimeout;
    }

    public boolean isConsumerEnabled() {
        return consumerEnabled;
    }

    public void setConsumerEnabled(boolean consumerEnabled) {
        this.consumerEnabled = consumerEnabled;
    }

    public boolean isStartupProbeEnabled() {
        return startupProbeEnabled;
    }

    public void setStartupProbeEnabled(boolean startupProbeEnabled) {
        this.startupProbeEnabled = startupProbeEnabled;
    }

    public String getStartupProbeTopic() {
        return startupProbeTopic;
    }

    public void setStartupProbeTopic(String startupProbeTopic) {
        this.startupProbeTopic = startupProbeTopic;
    }

    public Duration getStartupProbeTimeout() {
        return startupProbeTimeout;
    }

    public void setStartupProbeTimeout(Duration startupProbeTimeout) {
        this.startupProbeTimeout = startupProbeTimeout;
    }
}
