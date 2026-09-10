package io.github.actionguard.adapter.rabbitmq.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.actionguard.adapter.rabbitmq.consumer.RabbitMqActionExecutionConsumer;
import io.github.actionguard.adapter.rabbitmq.producer.RabbitMqActionExecutionMessageProducer;
import io.github.actionguard.adapter.rabbitmq.support.RabbitMqConsumeStrategy;
import io.github.actionguard.core.repository.ActionConsumeLogRepository;
import io.github.actionguard.core.runtime.execution.ActionExecutionCallback;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import io.github.actionguard.starter.config.ActionGuardAutoConfiguration;
import io.github.actionguard.starter.properties.ActionGuardProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.lang.reflect.Proxy;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMqActionExecutionAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RabbitMqActionExecutionAutoConfiguration.class,
                    ActionGuardAutoConfiguration.class, RabbitAutoConfiguration.class))
            .withPropertyValues("action.guard.store.type=memory",
                    "spring.rabbitmq.listener.simple.auto-startup=false",
                    "spring.rabbitmq.listener.direct.auto-startup=false");

    @Test
    void shouldNotEnableByClasspathAlone() {
        runner.run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(ActionExecutionMessageProducer.class)
                .doesNotHaveBean(RabbitMqActionExecutionConsumer.class));
    }

    @Test
    void shouldNotEnableByRabbitPropertiesAlone() {
        runner.withPropertyValues("spring.rabbitmq.host=unused.invalid").run(context -> assertThat(context)
                .hasNotFailed().doesNotHaveBean(ActionExecutionMessageProducer.class)
                .doesNotHaveBean(RabbitMqActionExecutionConsumer.class));
    }

    @Test
    void shouldEnableBothWithCaseInsensitiveSelectionAndRealAutoConfigurationOrder() {
        runner.withPropertyValues("action.guard.execution.transport=RaBbItMq").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ActionExecutionMessageProducer.class)
                    .hasSingleBean(RabbitMqActionExecutionConsumer.class);
            assertThat(context.getBean(ActionExecutionMessageProducer.class))
                    .isInstanceOf(RabbitMqActionExecutionMessageProducer.class);
            assertThat(context.getBean(ActionGuardProperties.class).getExecution().getTransport()).isEqualTo("RaBbItMq");
            assertThat(context.getBean(CachingConnectionFactory.class).isSimplePublisherConfirms()).isTrue();
            assertThat(context.getBean(RabbitListenerEndpointRegistry.class).getListenerContainers())
                    .hasSize(1).allSatisfy(container -> assertThat(container.isRunning()).isFalse());
        });
    }

    @Test
    void shouldRejectMissingTemplate() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ActionGuardAutoConfiguration.class,
                        RabbitMqActionExecutionAutoConfiguration.class))
                .withPropertyValues("action.guard.store.type=memory", "action.guard.execution.transport=rabbitmq")
                .run(context -> assertThat(context.getStartupFailure()).hasMessageContaining("需要配置 RabbitTemplate"));
    }

    @Test
    void shouldRejectExcludedAdapterEvenWithCustomProducer() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ActionGuardAutoConfiguration.class,
                        RabbitAutoConfiguration.class))
                .withBean(ActionExecutionMessageProducer.class, () -> outbox -> {
                })
                .withPropertyValues("action.guard.store.type=memory", "action.guard.execution.transport=rabbitmq")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasMessageContaining("需要启用 RabbitMqActionExecutionAutoConfiguration 自动配置"));
    }

    @Test
    void shouldBackOffForCustomProducer() {
        ActionExecutionMessageProducer producer = outbox -> {
        };
        runner.withPropertyValues("action.guard.execution.transport=rabbitmq")
                .withBean("customProducer", ActionExecutionMessageProducer.class, () -> producer).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ActionExecutionMessageProducer.class)
                            .hasSingleBean(RabbitMqActionExecutionConsumer.class);
                    assertThat(context.getBean(ActionExecutionMessageProducer.class)).isSameAs(producer);
                });
    }

    @Test
    void shouldBackOffForCustomConsumer() {
        runner.withUserConfiguration(CustomConsumer.class)
                .withPropertyValues("action.guard.execution.transport=rabbitmq").run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(RabbitMqActionExecutionConsumer.class);
                    assertThat(context.getBean(RabbitMqActionExecutionConsumer.class)).isSameAs(context.getBean("customConsumer"));
                });
    }

    @Test
    void shouldLetSpringResolvePrimaryTemplate() {
        runner.withUserConfiguration(MultipleTemplates.class)
                .withPropertyValues("action.guard.execution.transport=rabbitmq")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(ActionExecutionMessageProducer.class));
    }

    @Test
    void shouldRejectNonCachingConnectionFactory() {
        ConnectionFactory factory = (ConnectionFactory) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ConnectionFactory.class}, (proxy, method, args) -> {
                    throw new AssertionError("不应建立连接或访问连接工厂: " + method.getName());
                });
        runner.withBean(RabbitTemplate.class, () -> new RabbitTemplate(factory))
                .withPropertyValues("action.guard.execution.transport=rabbitmq")
                .run(context -> assertThat(context.getStartupFailure()).hasRootCauseMessage(
                        "Action Guard RabbitMQ producer requires a CachingConnectionFactory for publisher confirms"));
    }

    @Test
    void shouldDiscoverAdapterAndStarterThroughImports() {
        new ApplicationContextRunner().withUserConfiguration(DiscoveredApplication.class)
                .withPropertyValues("action.guard.store.type=memory", "action.guard.execution.transport=rabbitmq",
                        "spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.listener.direct.auto-startup=false")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(ActionGuardAutoConfiguration.class)
                        .hasSingleBean(RabbitMqActionExecutionAutoConfiguration.class)
                        .hasSingleBean(RabbitMqActionExecutionConsumer.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class DiscoveredApplication {
    }

    @Configuration(proxyBeanMethods = false)
    static class MultipleTemplates {
        @Bean
        @Primary
        RabbitTemplate primaryTemplate(ConnectionFactory factory) {
            return new RabbitTemplate(factory);
        }

        @Bean
        RabbitTemplate secondaryTemplate(ConnectionFactory factory) {
            return new RabbitTemplate(factory);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomConsumer {
        @Bean
        RabbitMqActionExecutionConsumer customConsumer(ObjectMapper mapper, ActionConsumeLogRepository repository,
                                                       ActionExecutionCallback callback, Clock clock, RabbitMqConsumeStrategy strategy,
                                                       ActionObservabilityService observability) {
            return new RabbitMqActionExecutionConsumer(mapper, repository, callback, "custom", clock, strategy, observability);
        }
    }
}
