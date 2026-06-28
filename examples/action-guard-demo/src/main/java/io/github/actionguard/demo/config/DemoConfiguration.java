package io.github.actionguard.demo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.actionguard.adapter.rabbitmq.config.ActionGuardRabbitMqProperties;
import io.github.actionguard.adapter.rabbitmq.consumer.RabbitMqActionExecutionConsumer;
import io.github.actionguard.adapter.rabbitmq.support.RabbitMqConsumeStrategy;
import io.github.actionguard.notify.model.NotifySendResult;
import io.github.actionguard.notify.model.NotifySmsRequest;
import io.github.actionguard.notify.sender.NotifySmsSender;
import io.github.actionguard.core.repository.ActionConsumeLogRepository;
import io.github.actionguard.core.runtime.execution.ActionExecutionCallback;
import io.github.actionguard.core.runtime.observability.ActionObservabilityService;
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
            RabbitMqConsumeStrategy rabbitMqConsumeStrategy,
            ActionObservabilityService actionObservabilityService
    ) {
        return new RabbitMqActionExecutionConsumer(
                objectMapper,
                actionConsumeLogRepository,
                actionExecutionCallback,
                properties.getConsumerGroup(),
                clock,
                rabbitMqConsumeStrategy,
                actionObservabilityService
        );
    }

    @Bean
    NotifySmsSender demoNotifySmsSender() {
        return new NotifySmsSender() {
            @Override
            public String provider() {
                return "mock-sms";
            }

            @Override
            public NotifySendResult send(NotifySmsRequest request) {
                System.out.println("send sms to " + request.phoneNumbers() + " template=" + request.templateId());
                return NotifySendResult.succeeded();
            }
        };
    }
}
