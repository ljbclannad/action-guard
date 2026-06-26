package io.github.actionguard.alert.webhook;

import io.github.actionguard.api.runtime.ActionAlertLevel;
import io.github.actionguard.api.spi.ActionAlertPublisher;
import org.springframework.web.client.RestClient;

public class WebhookActionAlertPublisher implements ActionAlertPublisher {

    private final RestClient restClient;
    private final String webhookUrl;

    public WebhookActionAlertPublisher(RestClient restClient, String webhookUrl) {
        this.restClient = restClient;
        this.webhookUrl = webhookUrl;
    }

    @Override
    public void publish(ActionAlertLevel level, String title, String message) {
        restClient.post()
                .uri(webhookUrl)
                .body(new AlertPayload(level.name(), title, message))
                .retrieve()
                .toBodilessEntity();
    }

    record AlertPayload(String level, String title, String message) {
    }
}
