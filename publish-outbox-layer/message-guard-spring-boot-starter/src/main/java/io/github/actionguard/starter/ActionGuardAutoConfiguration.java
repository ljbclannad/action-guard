package io.github.actionguard.starter;

import io.github.actionguard.api.ActionPublisher;
import io.github.actionguard.core.runtime.DefaultActionPublisher;
import io.github.actionguard.core.runtime.ActionDefinitionLoader;
import io.github.actionguard.core.runtime.YamlActionDefinitionLoader;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ActionGuardAutoConfiguration {

    @Bean
    public ActionPublisher actionPublisher() {
        return new DefaultActionPublisher();
    }

    @Bean
    public ActionDefinitionLoader actionDefinitionLoader() {
        return new YamlActionDefinitionLoader();
    }
}
