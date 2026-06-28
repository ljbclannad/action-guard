package io.github.actionguard.core.runtime.definition;

import io.github.actionguard.api.definition.ActionDefinition;
import io.github.actionguard.api.definition.ActionStepDefinition;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ActionDefinitionValidator {

    public void validate(ActionDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        if (definition.name() == null || definition.name().isBlank()) {
            throw new IllegalArgumentException("action definition name must not be blank");
        }
        if (definition.steps() == null || definition.steps().isEmpty()) {
            throw new IllegalArgumentException("action definition steps must not be empty: " + definition.name());
        }

        Set<String> stepNames = new HashSet<>();
        for (ActionStepDefinition step : definition.steps()) {
            if (step == null) {
                throw new IllegalArgumentException("action definition steps must not contain null: " + definition.name());
            }
            if (step.name() == null || step.name().isBlank()) {
                throw new IllegalArgumentException("action step name must not be blank: " + definition.name());
            }
            if (step.stepType() == null || step.stepType().isBlank()) {
                throw new IllegalArgumentException("action step type must not be blank: " + definition.name() + "/" + step.name());
            }
            if (step.target() == null || step.target().isBlank()) {
                throw new IllegalArgumentException("action step target must not be blank: " + definition.name() + "/" + step.name());
            }
            if (step.maxRetryCount() != null && step.maxRetryCount() < 0) {
                throw new IllegalArgumentException("action step maxRetryCount must be greater than or equal to 0: " + definition.name() + "/" + step.name());
            }
            if (step.retryBackoffMillis() != null && step.retryBackoffMillis() < 0) {
                throw new IllegalArgumentException("action step retryBackoffMillis must be greater than or equal to 0: " + definition.name() + "/" + step.name());
            }
            if (step.timeoutMillis() != null && step.timeoutMillis() <= 0) {
                throw new IllegalArgumentException("action step timeoutMillis must be greater than 0: " + definition.name() + "/" + step.name());
            }
            if (!stepNames.add(step.name())) {
                throw new IllegalStateException("duplicate action step name: " + definition.name() + "/" + step.name());
            }
        }
    }
}
