package io.github.actionguard.adapter.rocketmq.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.actionguard.adapter.rocketmq.consumer.RocketMqActionExecutionConsumer;
import io.github.actionguard.adapter.rocketmq.health.RocketMqStartupProbe;
import io.github.actionguard.adapter.rocketmq.producer.RocketMqActionExecutionMessageProducer;
import io.github.actionguard.adapter.rocketmq.support.RocketMqConsumeStrategy;
import io.github.actionguard.core.repository.ActionConsumeLogRepository;
import io.github.actionguard.core.runtime.execution.ActionExecutionCallback;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageFactory;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * RocketMQ 执行适配器的自动配置入口。
 */
@AutoConfiguration(afterName = "io.github.actionguard.starter.config.ActionGuardAutoConfiguration")
@ConditionalOnProperty(prefix = "action.guard.execution", name = "transport", havingValue = "rocketmq", matchIfMissing = false)
@EnableConfigurationProperties(ActionGuardRocketMqProperties.class)
public class RocketMqActionExecutionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ActionExecutionMessageFactory actionExecutionMessageFactory() {
        return new ActionExecutionMessageFactory();
    }

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper actionGuardRocketMqObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(ActionExecutionMessageProducer.class)
    public RocketMqActionExecutionMessageProducer actionExecutionMessageProducer(
            ObjectMapper actionGuardRocketMqObjectMapper,
            ActionExecutionMessageFactory actionExecutionMessageFactory,
            ActionGuardRocketMqProperties properties
    ) {
        if (properties.getNameServer() == null || properties.getNameServer().isBlank()) {
            throw new IllegalStateException("action.guard.rocketmq.name-server 不能为空");
        }
        if (properties.getTopic() == null || !properties.getTopic().matches("^[%|a-zA-Z0-9_-]+$")) {
            throw new IllegalStateException("action.guard.rocketmq.topic='" + properties.getTopic()
                    + "' 包含非法字符；RocketMQ topic 仅允许字母、数字、%、|、_ 和 -");
        }
        DefaultMQProducer producer = new DefaultMQProducer(properties.getProducerGroup());
        producer.setNamesrvAddr(properties.getNameServer());
        return new RocketMqActionExecutionMessageProducer(
                producer, actionGuardRocketMqObjectMapper, actionExecutionMessageFactory, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RocketMqConsumeStrategy rocketMqConsumeStrategy(ActionGuardRocketMqProperties properties) {
        return new RocketMqConsumeStrategy(properties.getMaxRedeliveries());
    }

    @Bean
    @ConditionalOnProperty(prefix = "action.guard.rocketmq", name = "startup-probe-enabled", havingValue = "true")
    public ApplicationRunner rocketMqStartupProbeRunner(ActionGuardRocketMqProperties properties) {
        // ApplicationRunner 在 Spring 上下文刷新完成后执行；此时主消费者已经进入生命周期启动阶段，
        // 再进行独立 Topic 的发收探测可以避免与框架 Bean 装配过程互相干扰。
        return arguments -> new RocketMqStartupProbe(properties).verify();
    }

    @Bean
    @ConditionalOnBean(ActionExecutionCallback.class)
    @ConditionalOnMissingBean(RocketMqActionExecutionConsumer.class)
    @ConditionalOnProperty(prefix = "action.guard.rocketmq", name = "consumer-enabled", havingValue = "true", matchIfMissing = true)
    public RocketMqActionExecutionConsumer rocketMqActionExecutionConsumer(
            ObjectMapper actionGuardRocketMqObjectMapper,
            ActionConsumeLogRepository actionConsumeLogRepository,
            ActionExecutionCallback actionExecutionCallback,
            ActionGuardRocketMqProperties properties,
            Clock clock,
            RocketMqConsumeStrategy rocketMqConsumeStrategy,
            ActionObservabilityService actionObservabilityService
    ) {
        return new RocketMqActionExecutionConsumer(
                actionGuardRocketMqObjectMapper, actionConsumeLogRepository, actionExecutionCallback, properties,
                clock, rocketMqConsumeStrategy, actionObservabilityService);
    }
}
