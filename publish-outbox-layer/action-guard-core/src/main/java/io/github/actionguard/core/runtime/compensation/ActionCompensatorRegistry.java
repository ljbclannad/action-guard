package io.github.actionguard.core.runtime.compensation;

import io.github.actionguard.api.spi.ActionCompensator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ActionCompensatorRegistry {

    private final Map<String, ActionCompensator> compensatorsByStepType;

    public ActionCompensatorRegistry(List<ActionCompensator> compensators) {
        Objects.requireNonNull(compensators, "compensators must not be null");
        this.compensatorsByStepType = new LinkedHashMap<>();
        for (ActionCompensator compensator : compensators) {
            if (compensator == null) {
                throw new IllegalArgumentException("compensators must not contain null");
            }
            String stepType = compensator.stepType();
            if (stepType == null || stepType.isBlank()) {
                throw new IllegalArgumentException("compensator stepType must not be blank");
            }
            if (compensatorsByStepType.putIfAbsent(stepType, compensator) != null) {
                throw new IllegalStateException("duplicate ActionCompensator for stepType: " + stepType);
            }
        }
    }

    public Optional<ActionCompensator> find(String stepType) {
        return Optional.ofNullable(compensatorsByStepType.get(stepType));
    }

    public ActionCompensator getRequired(String stepType) {
        return find(stepType)
                .orElseThrow(() -> new IllegalArgumentException("No ActionCompensator registered for stepType: " + stepType));
    }
}
