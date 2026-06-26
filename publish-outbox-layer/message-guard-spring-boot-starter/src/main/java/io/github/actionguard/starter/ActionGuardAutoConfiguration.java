package io.github.actionguard.starter;

import io.github.actionguard.api.ActionPublisher;
import io.github.actionguard.api.spi.ActionStepHandler;
import io.github.actionguard.api.definition.ActionDefinition;
import io.github.actionguard.core.repository.ActionInstanceRepository;
import io.github.actionguard.core.repository.ActionConsumeLogRepository;
import io.github.actionguard.core.repository.ActionOutboxRepository;
import io.github.actionguard.core.repository.ActionStepInstanceRepository;
import io.github.actionguard.core.repository.InMemoryActionConsumeLogRepository;
import io.github.actionguard.core.repository.InMemoryActionInstanceRepository;
import io.github.actionguard.core.repository.InMemoryActionOutboxRepository;
import io.github.actionguard.core.repository.InMemoryActionStepInstanceRepository;
import io.github.actionguard.core.runtime.DefaultActionPublisher;
import io.github.actionguard.core.runtime.ActionDefinitionLoader;
import io.github.actionguard.core.runtime.ActionDefinitionRegistry;
import io.github.actionguard.core.runtime.ActionDefinitionValidator;
import io.github.actionguard.core.runtime.ActionExecutionCallback;
import io.github.actionguard.core.runtime.ActionExecutionMessageProducer;
import io.github.actionguard.core.runtime.FixedAttemptActionRetryPolicy;
import io.github.actionguard.core.runtime.InMemoryActionDefinitionRegistry;
import io.github.actionguard.core.runtime.StepHandlerRegistry;
import io.github.actionguard.core.runtime.YamlActionDefinitionLoader;
import io.github.actionguard.core.runtime.DefaultActionExecutionCallback;
import io.github.actionguard.api.spi.ActionRetryPolicy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@AutoConfiguration
@EnableConfigurationProperties(ActionGuardProperties.class)
public class ActionGuardAutoConfiguration {

    @Bean
    public ActionPublisher actionPublisher(
            ActionDefinitionRegistry definitionRegistry,
            ActionInstanceRepository actionInstanceRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            ActionOutboxRepository actionOutboxRepository,
            Clock clock,
            Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer,
            ActionGuardProperties properties
    ) {
        return new TransactionalActionPublisher(new DefaultActionPublisher(
                definitionRegistry,
                actionInstanceRepository,
                actionStepInstanceRepository,
                actionOutboxRepository,
                clock
        ), actionInstanceRepository, actionOutboxRepository, actionExecutionMessageProducer, properties.getPublishRetryMaxAttempts());
    }

    @Bean
    public ActionDefinitionLoader actionDefinitionLoader() {
        return new YamlActionDefinitionLoader();
    }

    @Bean
    public ActionDefinitionValidator actionDefinitionValidator() {
        return new ActionDefinitionValidator();
    }

    @Bean
    public ActionDefinitionRegistry actionDefinitionRegistry(
            ActionDefinitionLoader loader,
            ActionDefinitionValidator validator,
            ActionGuardProperties properties
    ) {
        return new InMemoryActionDefinitionRegistry(loadDefinitions(loader, properties), validator);
    }

    @Bean
    public StepHandlerRegistry stepHandlerRegistry(List<ActionStepHandler> handlers) {
        return new StepHandlerRegistry(handlers);
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionRetryPolicy actionRetryPolicy() {
        return new FixedAttemptActionRetryPolicy(3);
    }

    @Bean
    public ActionExecutionCallback actionExecutionCallback(
            ActionInstanceRepository actionInstanceRepository,
            ActionStepInstanceRepository actionStepInstanceRepository,
            StepHandlerRegistry stepHandlerRegistry,
            ActionRetryPolicy actionRetryPolicy,
            ActionOutboxRepository actionOutboxRepository,
            Optional<ActionExecutionMessageProducer> actionExecutionMessageProducer,
            Clock clock
    ) {
        return new DefaultActionExecutionCallback(
                actionInstanceRepository,
                actionStepInstanceRepository,
                stepHandlerRegistry,
                actionRetryPolicy,
                actionOutboxRepository,
                actionExecutionMessageProducer,
                clock
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionInstanceRepository actionInstanceRepository() {
        return new InMemoryActionInstanceRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionStepInstanceRepository actionStepInstanceRepository() {
        return new InMemoryActionStepInstanceRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionOutboxRepository actionOutboxRepository() {
        return new InMemoryActionOutboxRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionConsumeLogRepository actionConsumeLogRepository() {
        return new InMemoryActionConsumeLogRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock actionGuardClock() {
        return Clock.systemUTC();
    }

    private List<ActionDefinition> loadDefinitions(ActionDefinitionLoader loader, ActionGuardProperties properties) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<ActionDefinition> definitions = new ArrayList<>();
        for (String locationPattern : properties.getDefinitionLocations()) {
            try {
                Resource[] resources = resolver.getResources(locationPattern);
                for (Resource resource : resources) {
                    definitions.add(loader.load(resource.getURL().toString()));
                }
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to resolve action definition locations: " + locationPattern, ex);
            }
        }
        return definitions;
    }
}
