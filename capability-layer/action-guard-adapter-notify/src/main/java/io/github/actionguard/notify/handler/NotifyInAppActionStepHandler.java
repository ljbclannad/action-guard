package io.github.actionguard.notify.handler;

import io.github.actionguard.api.runtime.ActionStepContext;
import io.github.actionguard.api.runtime.StepExecutionResult;
import io.github.actionguard.api.spi.ActionStepHandler;
import io.github.actionguard.notify.model.NotifyInAppRequest;
import io.github.actionguard.notify.model.NotifySendResult;
import io.github.actionguard.notify.sender.NotifyInAppSender;

import java.util.List;

public class NotifyInAppActionStepHandler extends AbstractNotifyActionStepHandler implements ActionStepHandler {

    public static final String STEP_TYPE = "NOTIFY_IN_APP_SEND";

    private final List<NotifyInAppSender> senders;

    public NotifyInAppActionStepHandler(List<NotifyInAppSender> senders) {
        this.senders = List.copyOf(senders);
    }

    @Override
    public String stepType() {
        return STEP_TYPE;
    }

    @Override
    public StepExecutionResult execute(ActionStepContext context) {
        try {
            NotifyInAppSender sender = resolveSender(providerKey(context));
            NotifySendResult result = sender.send(new NotifyInAppRequest(
                    context.actionName(),
                    context.bizKey(),
                    context.stepName(),
                    context.target(),
                    requiredStringList(context.payload(), "receiverIds"),
                    requiredString(context.payload(), "templateId"),
                    variables(context.payload())
            ));
            return toStepResult(result);
        } catch (IllegalArgumentException ex) {
            return StepExecutionResult.failed("NOTIFY_REQUEST_INVALID", ex.getMessage());
        }
    }

    private NotifyInAppSender resolveSender(String provider) {
        return senders.stream()
                .filter(sender -> provider.equals(sender.provider()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no in-app notify provider registered for target: " + provider));
    }

    private StepExecutionResult toStepResult(NotifySendResult result) {
        return result.success() ? StepExecutionResult.succeeded() : StepExecutionResult.failed(result.errorCode(), result.errorMessage());
    }
}
