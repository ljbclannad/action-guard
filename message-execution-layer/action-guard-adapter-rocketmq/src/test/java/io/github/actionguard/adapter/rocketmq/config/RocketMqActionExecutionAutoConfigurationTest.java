package io.github.actionguard.adapter.rocketmq.config;

import io.github.actionguard.adapter.rocketmq.producer.RocketMqActionExecutionMessageProducer;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import io.github.actionguard.starter.config.ActionGuardAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RocketMqActionExecutionAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ActionGuardAutoConfiguration.class,
                    RocketMqActionExecutionAutoConfiguration.class))
            .withPropertyValues("action.guard.store.type=memory", "action.guard.rocketmq.consumer-enabled=false");

    @Test
    void shouldNotEnableByClasspathAlone() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ActionExecutionMessageProducer.class));
    }

    @Test
    void shouldRequireNameServerWhenRocketMqIsSelected() {
        runner.withPropertyValues("action.guard.execution.transport=rocketmq").run(context ->
                assertThat(context.getStartupFailure()).hasMessageContaining("name-server 不能为空"));
    }

    @Test
    void shouldRejectTopicContainingDotBeforeConnecting() {
        runner.withPropertyValues("action.guard.execution.transport=rocketmq",
                        "action.guard.rocketmq.name-server=unused.invalid:9876",
                        "action.guard.rocketmq.topic=action.guard.execute")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasMessageContaining("action.guard.rocketmq.topic='action.guard.execute' 包含非法字符"));
    }

    @Test
    void shouldEnableProducerWithoutConnectingAtStartup() {
        runner.withPropertyValues("action.guard.execution.transport=RoCkEtMq",
                        "action.guard.rocketmq.name-server=unused.invalid:9876")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(ActionExecutionMessageProducer.class)
                        .getBean(ActionExecutionMessageProducer.class)
                        .isInstanceOf(RocketMqActionExecutionMessageProducer.class));
    }
}
