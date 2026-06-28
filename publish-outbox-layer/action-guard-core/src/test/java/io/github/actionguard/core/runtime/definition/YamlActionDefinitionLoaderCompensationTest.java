package io.github.actionguard.core.runtime.definition;

import io.github.actionguard.api.definition.ActionDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YamlActionDefinitionLoaderCompensationTest {

    @Test
    void shouldLoadCompensationEnabledFromYaml() {
        YamlActionDefinitionLoader loader = new YamlActionDefinitionLoader();

        ActionDefinition definition = loader.load("actions/compensation-enabled.yml");

        assertThat(definition.compensationEnabled()).isTrue();
    }
}
