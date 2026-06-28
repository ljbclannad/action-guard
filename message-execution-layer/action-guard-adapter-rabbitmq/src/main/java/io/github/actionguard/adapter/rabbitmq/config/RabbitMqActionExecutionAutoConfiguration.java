package io.github.actionguard.adapter.rabbitmq.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.actionguard.adapter.rabbitmq.consumer.RabbitMqActionExecutionConsumer;
import io.github.actionguard.adapter.rabbitmq.producer.RabbitMqActionExecutionMessageProducer;
import io.github.actionguard.adapter.rabbitmq.support.RabbitMqConsumeStrategy;
import io.github.actionguard.core.repository.ActionConsumeLogRepository;
import io.github.actionguard.core.runtime.execution.ActionExecutionCallback;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageFactory;
import io.github.actionguard.core.runtime.execution.ActionExecutionMessageProducer;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * RabbitMQ 执行适配器的自动配置入口。
 *
 * <p>它处在“core runtime 抽象能力 -> RabbitMQ 具体实现”的装配边界上：当前应用同时具备
 * RabbitTemplate 和 Action Guard 核心 Bean 时，这里会自动补齐消息生产者、消费者、序列化器和消费策略，
 * 把 action 执行消息真正接到 RabbitMQ。
 *
 * <p>因此它不参与 action 状态推进本身，而是把 core 层定义好的
 * {@code ActionExecutionMessageProducer}/{@code ActionExecutionCallback} 协议映射到 MQ 基础设施。
 */
@AutoConfiguration(after = RabbitAutoConfiguration.class)
@EnableConfigurationProperties(ActionGuardRabbitMqProperties.class)
@ConditionalOnBean(RabbitTemplate.class)
public class RabbitMqActionExecutionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ActionExecutionMessageFactory actionExecutionMessageFactory() {
        return new ActionExecutionMessageFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper actionGuardRabbitMqObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    public ActionExecutionMessageProducer actionExecutionMessageProducer(
            RabbitTemplate rabbitTemplate,
            ObjectMapper actionGuardRabbitMqObjectMapper,
            ActionExecutionMessageFactory actionExecutionMessageFactory,
            ActionGuardRabbitMqProperties properties
    ) {
        return new RabbitMqActionExecutionMessageProducer(
                rabbitTemplate,
                actionGuardRabbitMqObjectMapper,
                actionExecutionMessageFactory,
                properties
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public RabbitMqConsumeStrategy rabbitMqConsumeStrategy(ActionGuardRabbitMqProperties properties) {
        return new RabbitMqConsumeStrategy(properties.getMaxRedeliveries());
    }

    @Bean
    @ConditionalOnBean(ActionExecutionCallback.class)
    public RabbitMqActionExecutionConsumer rabbitMqActionExecutionConsumer(
            ObjectMapper actionGuardRabbitMqObjectMapper,
            ActionConsumeLogRepository actionConsumeLogRepository,
            ActionExecutionCallback actionExecutionCallback,
            ActionGuardRabbitMqProperties properties,
            Clock clock,
            RabbitMqConsumeStrategy rabbitMqConsumeStrategy,
            ActionObservabilityService actionObservabilityService
    ) {
        return new RabbitMqActionExecutionConsumer(
                actionGuardRabbitMqObjectMapper,
                actionConsumeLogRepository,
                actionExecutionCallback,
                properties.getConsumerGroup(),
                clock,
                rabbitMqConsumeStrategy,
                actionObservabilityService
        );
    }
}
