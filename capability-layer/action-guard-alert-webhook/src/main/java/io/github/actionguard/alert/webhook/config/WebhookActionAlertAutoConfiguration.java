package io.github.actionguard.alert.webhook.config;

import io.github.actionguard.alert.webhook.properties.ActionGuardWebhookAlertProperties;
import io.github.actionguard.alert.webhook.publisher.WebhookActionAlertSender;
import io.github.actionguard.api.spi.ActionAlertSender;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@EnableConfigurationProperties(ActionGuardWebhookAlertProperties.class)
@ConditionalOnProperty(prefix = "action.guard.alert.webhook", name = "enabled", havingValue = "true")
public class WebhookActionAlertAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    RestClient actionGuardAlertRestClient() {
        return RestClient.builder().build();
    }

    @Bean
    @ConditionalOnMissingBean(ActionAlertSender.class)
    ActionAlertSender actionAlertSender(
            RestClient actionGuardAlertRestClient,
            ActionGuardWebhookAlertProperties properties
    ) {
        return new WebhookActionAlertSender(actionGuardAlertRestClient, properties.getUrl());
    }
}
