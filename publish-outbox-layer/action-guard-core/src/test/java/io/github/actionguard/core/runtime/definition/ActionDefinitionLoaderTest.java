package io.github.actionguard.core.runtime.definition;

import io.github.actionguard.api.definition.ActionDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActionDefinitionLoaderTest {

    @Test
    void shouldLoadSerialActionDefinitionFromYaml() {
        ActionDefinitionLoader loader = new YamlActionDefinitionLoader();

        ActionDefinition definition = loader.load("actions/order-cancel.yml");

        assertThat(definition.name()).isEqualTo("order-cancel-flow");
        assertThat(definition.steps()).hasSize(2);
        assertThat(definition.steps().get(1).stepType()).isEqualTo("SMS");
        assertThat(definition.steps().get(0).maxRetryCount()).isEqualTo(2);
        assertThat(definition.steps().get(0).retryBackoffMillis()).isEqualTo(250L);
        assertThat(definition.steps().get(0).timeoutMillis()).isEqualTo(1000L);
    }
}
