package io.github.actionguard.adapter.rabbitmq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "action.guard.rabbitmq")
public class ActionGuardRabbitMqProperties {

    private String exchange = "action.guard.execute";
    private String routingKeyPrefix = "action.execute";
    private String queue = "action.guard.execute.queue";
    private String consumerGroup = "action-guard-rabbitmq";
    private int maxRedeliveries = 1;
    private Duration confirmTimeout = Duration.ofSeconds(5);

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getRoutingKeyPrefix() {
        return routingKeyPrefix;
    }

    public void setRoutingKeyPrefix(String routingKeyPrefix) {
        this.routingKeyPrefix = routingKeyPrefix;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
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

    public Duration getConfirmTimeout() { return confirmTimeout; }

    public void setConfirmTimeout(Duration confirmTimeout) { this.confirmTimeout = confirmTimeout; }
}
