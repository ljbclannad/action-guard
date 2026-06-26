package io.github.actionguard.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "action.guard")
public class ActionGuardProperties {

    private List<String> definitionLocations = List.of("classpath*:actions/*.yml", "classpath*:actions/*.yaml");
    private int publishRetryMaxAttempts = 1;

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
}
