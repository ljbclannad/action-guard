package io.github.actionguard.notify;

import io.github.actionguard.api.runtime.ActionStepContext;
import io.github.actionguard.api.runtime.StepExecutionResult;
import io.github.actionguard.api.spi.ActionStepHandler;

import java.util.List;

public class NotifyEmailActionStepHandler extends AbstractNotifyActionStepHandler implements ActionStepHandler {

    public static final String STEP_TYPE = "NOTIFY_EMAIL_SEND";

    private final List<NotifyEmailSender> senders;

    public NotifyEmailActionStepHandler(List<NotifyEmailSender> senders) {
        this.senders = List.copyOf(senders);
    }

    @Override
    public String stepType() {
        return STEP_TYPE;
    }

    @Override
    public StepExecutionResult execute(ActionStepContext context) {
        try {
            NotifyEmailSender sender = resolveSender(providerKey(context));
            NotifySendResult result = sender.send(new NotifyEmailRequest(
                    context.actionName(),
                    context.bizKey(),
                    context.stepName(),
                    context.target(),
                    requiredStringList(context.payload(), "recipients"),
                    requiredString(context.payload(), "subject"),
                    optionalString(context.payload(), "body"),
                    optionalString(context.payload(), "templateId"),
                    variables(context.payload())
            ));
            return toStepResult(result);
        } catch (IllegalArgumentException ex) {
            return StepExecutionResult.failed("NOTIFY_REQUEST_INVALID", ex.getMessage());
        }
    }

    private NotifyEmailSender resolveSender(String provider) {
        return senders.stream()
                .filter(sender -> provider.equals(sender.provider()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no email notify provider registered for target: " + provider));
    }

    private StepExecutionResult toStepResult(NotifySendResult result) {
        return result.success() ? StepExecutionResult.succeeded() : StepExecutionResult.failed(result.errorCode(), result.errorMessage());
    }
}
