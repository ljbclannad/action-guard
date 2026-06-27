package io.github.actionguard.alert.webhook;

import io.github.actionguard.api.spi.ActionAlertPublisher;
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
    @ConditionalOnMissingBean(ActionAlertPublisher.class)
    ActionAlertPublisher actionAlertPublisher(
            RestClient actionGuardAlertRestClient,
            ActionGuardWebhookAlertProperties properties
    ) {
        return new WebhookActionAlertPublisher(actionGuardAlertRestClient, properties.getUrl());
    }
}
