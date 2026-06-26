package io.github.actionguard.api.definition;

public record ActionStepDefinition(
        String name,
        String stepType,
        String target
) {
}
