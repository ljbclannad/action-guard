package io.github.actionguard.alert.webhook;

import io.github.actionguard.api.runtime.ActionAlertEvent;
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
    public void publish(ActionAlertEvent event) {
        restClient.post()
                .uri(webhookUrl)
                .body(new AlertPayload(
                        event.type().name(),
                        event.level().name(),
                        event.title(),
                        event.message(),
                        event.actionName(),
                        event.actionInstanceId(),
                        event.stepName(),
                        event.stepType(),
                        event.occurredAt() == null ? null : event.occurredAt().toString(),
                        event.details()
                ))
                .retrieve()
                .toBodilessEntity();
    }

    record AlertPayload(
            String type,
            String level,
            String title,
            String message,
            String actionName,
            String actionInstanceId,
            String stepName,
            String stepType,
            String occurredAt,
            java.util.Map<String, String> details
    ) {
    }
}
