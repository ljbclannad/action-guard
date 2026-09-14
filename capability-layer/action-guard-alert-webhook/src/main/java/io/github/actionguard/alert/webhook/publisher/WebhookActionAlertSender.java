package io.github.actionguard.alert.webhook.publisher;

import io.github.actionguard.api.runtime.ActionAlertEvent;
import io.github.actionguard.api.spi.ActionAlertSender;
import org.springframework.web.client.RestClient;

/**
 * Webhook 告警发送器：只进行一次 HTTP 外发，不在适配器内重试。
 */
public class WebhookActionAlertSender implements ActionAlertSender {

    private final RestClient restClient;
    private final String webhookUrl;

    public WebhookActionAlertSender(RestClient restClient, String webhookUrl) {
        this.restClient = restClient;
        this.webhookUrl = webhookUrl;
    }

    @Override
    public void send(ActionAlertEvent event) {
        restClient.post()
                .uri(webhookUrl)
                .body(new AlertPayload(
                        event.eventId(),
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
            String eventId,
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
