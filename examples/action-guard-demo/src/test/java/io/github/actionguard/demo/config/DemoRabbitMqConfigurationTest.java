package io.github.actionguard.demo.config;

import io.github.actionguard.adapter.rabbitmq.config.RabbitMqActionExecutionAutoConfiguration;
import io.github.actionguard.adapter.rabbitmq.consumer.RabbitMqActionExecutionConsumer;
import io.github.actionguard.notify.sender.NotifySmsSender;
import io.github.actionguard.starter.config.ActionGuardAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DemoRabbitMqConfigurationTest {
    // 不加载 application.yml，真正模拟未配置选择项；监听容器禁止启动，不连接真实 broker。
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DemoConfiguration.class)
            .withConfiguration(AutoConfigurations.of(RabbitMqActionExecutionAutoConfiguration.class,
                    ActionGuardAutoConfiguration.class, RabbitAutoConfiguration.class))
            .withPropertyValues("action.guard.store.type=memory",
                    "spring.rabbitmq.listener.simple.auto-startup=false",
                    "spring.rabbitmq.listener.direct.auto-startup=false");

    @Test
    void shouldKeepSmsWithoutTopologyOrConsumerWhenNotSelected() {
        runner.run(context -> assertThat(context).hasNotFailed().hasSingleBean(NotifySmsSender.class)
                .doesNotHaveBean(Declarables.class).doesNotHaveBean(RabbitMqActionExecutionConsumer.class));
    }

    @Test
    void shouldCreateOneConsumerAndTopologyWhenSelected() {
        runner.withPropertyValues("action.guard.execution.transport=rabbitmq").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(Declarables.class)
                    .hasSingleBean(RabbitMqActionExecutionConsumer.class).hasSingleBean(NotifySmsSender.class);
            assertThat(context.getBean(Declarables.class).getDeclarables()).hasSize(3);
            assertThat(context.getBean(RabbitListenerEndpointRegistry.class).getListenerContainers())
                    .hasSize(1).allSatisfy(container -> assertThat(container.isRunning()).isFalse());
        });
    }
}
