package io.github.actionguard.adapter.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.actionguard.core.repository.ActionConsumeLogRepository;
import io.github.actionguard.core.runtime.ActionExecutionCallback;
import io.github.actionguard.core.runtime.ActionExecutionMessageFactory;
import io.github.actionguard.core.runtime.ActionExecutionMessageProducer;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

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
            RabbitMqConsumeStrategy rabbitMqConsumeStrategy
    ) {
        return new RabbitMqActionExecutionConsumer(
                actionGuardRabbitMqObjectMapper,
                actionConsumeLogRepository,
                actionExecutionCallback,
                properties.getConsumerGroup(),
                clock,
                rabbitMqConsumeStrategy
        );
    }
}
