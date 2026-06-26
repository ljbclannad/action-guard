package io.github.actionguard.core.runtime;

import io.github.actionguard.api.definition.ActionDefinition;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class InMemoryActionDefinitionRegistry implements ActionDefinitionRegistry {

    private final Map<String, ActionDefinition> definitionsByName;

    public InMemoryActionDefinitionRegistry(List<ActionDefinition> definitions, ActionDefinitionValidator validator) {
        Objects.requireNonNull(definitions, "definitions must not be null");
        Objects.requireNonNull(validator, "validator must not be null");

        Map<String, ActionDefinition> indexedDefinitions = new LinkedHashMap<>();
        for (ActionDefinition definition : definitions) {
            validator.validate(definition);
            ActionDefinition previous = indexedDefinitions.putIfAbsent(definition.name(), definition);
            if (previous != null) {
                throw new IllegalStateException("duplicate action definition name: " + definition.name());
            }
        }
        this.definitionsByName = Map.copyOf(indexedDefinitions);
    }

    @Override
    public Optional<ActionDefinition> find(String actionName) {
        return Optional.ofNullable(definitionsByName.get(actionName));
    }

    @Override
    public ActionDefinition getRequired(String actionName) {
        return find(actionName)
                .orElseThrow(() -> new IllegalArgumentException("No ActionDefinition registered for actionName: " + actionName));
    }

    @Override
    public Collection<ActionDefinition> getAll() {
        return definitionsByName.values();
    }
}
