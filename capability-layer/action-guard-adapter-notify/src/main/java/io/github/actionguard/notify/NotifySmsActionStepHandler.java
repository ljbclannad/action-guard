package io.github.actionguard.notify;

import io.github.actionguard.api.runtime.ActionStepContext;
import io.github.actionguard.api.runtime.StepExecutionResult;
import io.github.actionguard.api.spi.ActionStepHandler;

import java.util.List;

public class NotifySmsActionStepHandler extends AbstractNotifyActionStepHandler implements ActionStepHandler {

    public static final String STEP_TYPE = "NOTIFY_SMS_SEND";

    private final List<NotifySmsSender> senders;

    public NotifySmsActionStepHandler(List<NotifySmsSender> senders) {
        this.senders = List.copyOf(senders);
    }

    @Override
    public String stepType() {
        return STEP_TYPE;
    }

    @Override
    public StepExecutionResult execute(ActionStepContext context) {
        try {
            NotifySmsSender sender = resolveSender(providerKey(context));
            NotifySendResult result = sender.send(new NotifySmsRequest(
                    context.actionName(),
                    context.bizKey(),
                    context.stepName(),
                    context.target(),
                    requiredStringList(context.payload(), "phoneNumbers"),
                    optionalString(context.payload(), "sign"),
                    requiredString(context.payload(), "templateId"),
                    variables(context.payload())
            ));
            return toStepResult(result);
        } catch (IllegalArgumentException ex) {
            return StepExecutionResult.failed("NOTIFY_REQUEST_INVALID", ex.getMessage());
        }
    }

    private NotifySmsSender resolveSender(String provider) {
        return senders.stream()
                .filter(sender -> provider.equals(sender.provider()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no sms notify provider registered for target: " + provider));
    }

    private StepExecutionResult toStepResult(NotifySendResult result) {
        return result.success() ? StepExecutionResult.succeeded() : StepExecutionResult.failed(result.errorCode(), result.errorMessage());
    }
}
