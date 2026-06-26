package io.github.actionguard.api.definition;

import java.util.List;

public record ActionDefinition(
        String name,
        String description,
        List<ActionStepDefinition> steps
) {
}
