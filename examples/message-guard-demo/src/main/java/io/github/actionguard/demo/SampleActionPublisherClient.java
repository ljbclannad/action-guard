package io.github.actionguard.demo;

import io.github.actionguard.api.ActionPublisher;
import io.github.actionguard.api.ActionRequest;
import io.github.actionguard.api.ActionStepRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SampleActionPublisherClient {

    private final ActionPublisher actionPublisher;

    public SampleActionPublisherClient(ActionPublisher actionPublisher) {
        this.actionPublisher = actionPublisher;
    }

    public void publishOrderCancel() {
        actionPublisher.publish(new ActionRequest(
                "order-cancel-flow",
                "order:1",
                Map.of("operator", "demo"),
                List.of(
                        new ActionStepRequest("send-cancel-event", "MQ_MESSAGE", "order.cancel.exchange", Map.of("orderId", "1")),
                        new ActionStepRequest("send-user-sms", "SMS", "notify.user", Map.of("template", "order-cancel"))
                )
        ));
    }
}
