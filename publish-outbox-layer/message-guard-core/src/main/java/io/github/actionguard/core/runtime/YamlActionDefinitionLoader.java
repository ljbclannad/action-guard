package io.github.actionguard.core.runtime;

import io.github.actionguard.api.definition.ActionDefinition;
import io.github.actionguard.api.definition.ActionStepDefinition;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class YamlActionDefinitionLoader implements ActionDefinitionLoader {

    @Override
    @SuppressWarnings("unchecked")
    public ActionDefinition load(String location) {
        Yaml yaml = new Yaml();
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(location)) {
            if (stream == null) {
                throw new IllegalArgumentException("Action definition not found: " + location);
            }
            Map<String, Object> raw = yaml.load(stream);
            List<Map<String, Object>> steps = (List<Map<String, Object>>) raw.get("steps");
            List<ActionStepDefinition> definitions = steps.stream()
                    .map(step -> new ActionStepDefinition(
                            String.valueOf(step.get("name")),
                            String.valueOf(step.get("stepType")),
                            String.valueOf(step.get("target"))
                    ))
                    .toList();
            return new ActionDefinition(
                    String.valueOf(raw.get("name")),
                    String.valueOf(raw.get("description")),
                    definitions
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load action definition: " + location, ex);
        }
    }
}
