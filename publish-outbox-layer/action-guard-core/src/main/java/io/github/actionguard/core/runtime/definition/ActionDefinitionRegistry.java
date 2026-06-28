package io.github.actionguard.core.runtime.definition;

import io.github.actionguard.api.definition.ActionDefinition;

import java.util.Collection;
import java.util.Optional;

public interface ActionDefinitionRegistry {

    Optional<ActionDefinition> find(String actionName);

    ActionDefinition getRequired(String actionName);

    Collection<ActionDefinition> getAll();
}
