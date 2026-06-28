package io.github.actionguard.core.runtime;

import io.github.actionguard.api.definition.ActionDefinition;

public interface ActionDefinitionLoader {

    ActionDefinition load(String location);
}
