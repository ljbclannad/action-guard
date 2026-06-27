package io.github.actionguard.api.spi;

import java.util.Map;

public interface ActionMetricsRecorder {

    void increment(String metricName, Map<String, String> tags);
}
