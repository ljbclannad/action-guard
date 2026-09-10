package io.github.actionguard.demo.config;

import io.github.actionguard.adapter.rabbitmq.config.ActionGuardRabbitMqProperties;
import io.github.actionguard.notify.model.NotifySendResult;
import io.github.actionguard.notify.model.NotifySmsRequest;
import io.github.actionguard.notify.sender.NotifySmsSender;
import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DemoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "action.guard.execution", name = "transport", havingValue = "rabbitmq", matchIfMissing = false)
    Declarables actionGuardRabbitTopology(ActionGuardRabbitMqProperties properties) {
        TopicExchange exchange = new TopicExchange(properties.getExchange(), true, false);
        Queue queue = new Queue(properties.getQueue(), true);
        Binding binding = BindingBuilder.bind(queue)
                .to(exchange)
                .with(properties.getRoutingKeyPrefix() + ".#");
        return new Declarables(exchange, queue, binding);
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
