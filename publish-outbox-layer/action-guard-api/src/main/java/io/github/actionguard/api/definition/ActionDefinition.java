package io.github.actionguard.api.definition;

import java.util.List;

public record ActionDefinition(
        String name,
        int version,
        String description,
        boolean compensationEnabled,
        List<ActionStepDefinition> steps
) {

    public ActionDefinition(String name, String description, boolean compensationEnabled, List<ActionStepDefinition> steps) {
        this(name, 1, description, compensationEnabled, steps);
    }
}
