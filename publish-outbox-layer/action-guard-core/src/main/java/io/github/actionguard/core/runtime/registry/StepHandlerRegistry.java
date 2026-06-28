package io.github.actionguard.core.runtime.registry;

import io.github.actionguard.api.spi.ActionStepHandler;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class StepHandlerRegistry {

    private final Map<String, ActionStepHandler> handlersByType;

    public StepHandlerRegistry(Collection<ActionStepHandler> handlers) {
        Objects.requireNonNull(handlers, "handlers must not be null");

        Map<String, ActionStepHandler> indexedHandlers = new LinkedHashMap<>();
        for (ActionStepHandler handler : handlers) {
            if (handler == null) {
                throw new IllegalArgumentException("handlers must not contain null");
            }
            String stepType = handler.stepType();
            if (stepType == null || stepType.isBlank()) {
                throw new IllegalArgumentException("handler stepType must not be blank");
            }
            ActionStepHandler previous = indexedHandlers.putIfAbsent(stepType, handler);
            if (previous != null) {
                throw new IllegalStateException("duplicate ActionStepHandler for stepType: " + stepType);
            }
        }
        this.handlersByType = Map.copyOf(indexedHandlers);
    }

    public Optional<ActionStepHandler> find(String stepType) {
        return Optional.ofNullable(handlersByType.get(stepType));
    }

    public ActionStepHandler getRequired(String stepType) {
        return find(stepType)
                .orElseThrow(() -> new IllegalArgumentException("No ActionStepHandler registered for stepType: " + stepType));
    }
}
