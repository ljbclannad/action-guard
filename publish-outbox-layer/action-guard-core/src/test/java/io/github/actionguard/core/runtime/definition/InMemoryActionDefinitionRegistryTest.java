package io.github.actionguard.core.runtime.definition;

import io.github.actionguard.api.definition.ActionDefinition;
import io.github.actionguard.api.definition.ActionStepDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryActionDefinitionRegistryTest {

    private final ActionDefinitionValidator validator = new ActionDefinitionValidator();

    @Test
    void shouldFindDefinitionByName() {
        ActionDefinition definition = new ActionDefinition(
                "order-cancel-flow",
                "demo",
                false,
                List.of(new ActionStepDefinition("notify", "SMS", "notify.user", null, null, null))
        );
        ActionDefinitionRegistry registry = new InMemoryActionDefinitionRegistry(List.of(definition), validator);

        assertThat(registry.find("order-cancel-flow")).contains(definition);
        assertThat(registry.getRequired("order-cancel-flow")).isSameAs(definition);
        assertThat(registry.getAll()).containsExactly(definition);
    }

    @Test
    void shouldRejectDuplicateDefinitionNames() {
        ActionDefinition first = new ActionDefinition(
                "order-cancel-flow",
                "demo",
                false,
                List.of(new ActionStepDefinition("notify-1", "SMS", "notify.user", null, null, null))
        );
        ActionDefinition second = new ActionDefinition(
                "order-cancel-flow",
                "demo",
                false,
                List.of(new ActionStepDefinition("notify-2", "EMAIL", "notify.email", null, null, null))
        );

        assertThatThrownBy(() -> new InMemoryActionDefinitionRegistry(List.of(first, second), validator))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate action definition name");
    }
}
