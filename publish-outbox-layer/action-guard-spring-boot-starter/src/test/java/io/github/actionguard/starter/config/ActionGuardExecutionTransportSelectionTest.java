package io.github.actionguard.starter.config;

import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import io.github.actionguard.starter.properties.ActionGuardProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionGuardExecutionTransportSelectionTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ActionGuardAutoConfiguration.class))
            .withPropertyValues("action.guard.store.type=memory");

    @Test
    void shouldLeaveTransportUnselected() {
        runner.withClassLoader(new FilteredClassLoader("io.github.actionguard.adapter.rabbitmq", "org.springframework.amqp"))
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(ActionExecutionMessageProducer.class);
                    assertThat(context.getBean(ActionGuardProperties.class).getExecution().getTransport()).isNull();
                });
    }

    @Test
    void shouldAllowCustomProducerWithoutSelection() {
        ActionExecutionMessageProducer producer = outbox -> {
        };
        runner.withBean(ActionExecutionMessageProducer.class, () -> producer).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ActionExecutionMessageProducer.class);
            assertThat(context.getBean(ActionExecutionMessageProducer.class)).isSameAs(producer);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "kafka", "rabbitmqq", "rocketmqq", " rabbitmq", "rabbitmq ", " rocketmq", "rocketmq "})
    void shouldRejectInvalidSelectionWithoutTrimming(String value) {
        // 直接设置属性源保留空白，避免测试工具解析 key=value 时自动去除空白。
        runner.withInitializer(context -> context.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("transport", Map.of("action.guard.execution.transport", value))))
                .run(context -> assertThat(context.getStartupFailure()).hasMessageContaining("仅支持 rabbitmq"));
    }

    @Test
    void shouldRejectMissingAdapterEvenWithCustomProducer() {
        runner.withClassLoader(new FilteredClassLoader("io.github.actionguard.adapter.rabbitmq"))
                .withPropertyValues("action.guard.execution.transport=rabbitmq")
                .withBean(ActionExecutionMessageProducer.class, () -> outbox -> {
                })
                .run(context -> assertThat(context.getStartupFailure())
                        .hasMessageContaining("需要引入 action-guard-adapter-rabbitmq 模块"));
    }

    @Test
    void shouldRejectMissingRocketMqAdapter() {
        runner.withClassLoader(new FilteredClassLoader("io.github.actionguard.adapter.rocketmq", "org.apache.rocketmq"))
                .withPropertyValues("action.guard.execution.transport=rocketmq")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasMessageContaining("需要引入 action-guard-adapter-rocketmq 模块"));
    }
}
