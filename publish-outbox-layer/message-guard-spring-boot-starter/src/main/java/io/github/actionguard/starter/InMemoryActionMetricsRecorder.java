package io.github.actionguard.starter;

import io.github.actionguard.api.spi.ActionMetricsRecorder;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryActionMetricsRecorder implements ActionMetricsRecorder {

    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public void increment(String metricName, Map<String, String> tags) {
        counters.computeIfAbsent(metricKey(metricName, tags), ignored -> new AtomicLong()).incrementAndGet();
    }

    public long counterValue(String metricName, Map<String, String> tags) {
        return counters.getOrDefault(metricKey(metricName, tags), new AtomicLong()).get();
    }

    private String metricKey(String metricName, Map<String, String> tags) {
        return metricName + "|" + new TreeMap<>(tags);
    }
}
