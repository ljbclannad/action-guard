package io.github.actionguard.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.actionguard.adapter.rabbitmq.RabbitMqActionExecutionConsumer;
import io.github.actionguard.api.runtime.ActionStepContext;
import io.github.actionguard.api.runtime.StepExecutionResult;
import io.github.actionguard.api.spi.ActionStepHandler;
import io.github.actionguard.adapter.rabbitmq.ActionGuardRabbitMqProperties;
import io.github.actionguard.adapter.rabbitmq.RabbitMqConsumeStrategy;
import io.github.actionguard.core.repository.ActionConsumeLogRepository;
import io.github.actionguard.core.runtime.ActionExecutionCallback;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class DemoConfiguration {

    @Bean
    Declarables actionGuardRabbitTopology(ActionGuardRabbitMqProperties properties) {
        TopicExchange exchange = new TopicExchange(properties.getExchange(), true, false);
        Queue queue = new Queue(properties.getQueue(), true);
        Binding binding = BindingBuilder.bind(queue)
                .to(exchange)
                .with(properties.getRoutingKeyPrefix() + ".#");
        return new Declarables(exchange, queue, binding);
    }

    @Bean
    RabbitMqActionExecutionConsumer rabbitMqActionExecutionConsumer(
            ObjectMapper objectMapper,
            ActionConsumeLogRepository actionConsumeLogRepository,
            ActionExecutionCallback actionExecutionCallback,
            ActionGuardRabbitMqProperties properties,
            Clock clock,
            RabbitMqConsumeStrategy rabbitMqConsumeStrategy
    ) {
        return new RabbitMqActionExecutionConsumer(
                objectMapper,
                actionConsumeLogRepository,
                actionExecutionCallback,
                properties.getConsumerGroup(),
                clock,
                rabbitMqConsumeStrategy
        );
    }

    @Bean
    ActionStepHandler smsActionStepHandler() {
        return new ActionStepHandler() {
            @Override
            public String stepType() {
                return "SMS";
            }

            @Override
            public StepExecutionResult execute(ActionStepContext context) {
                System.out.println("send sms to " + context.attributes().get("phone"));
                return StepExecutionResult.succeeded();
            }
        };
    }
}
