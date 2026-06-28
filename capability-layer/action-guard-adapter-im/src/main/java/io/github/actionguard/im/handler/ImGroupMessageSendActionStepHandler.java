package io.github.actionguard.im.handler;

import io.github.actionguard.api.runtime.ActionStepContext;
import io.github.actionguard.api.runtime.StepExecutionResult;
import io.github.actionguard.api.spi.ActionStepHandler;
import io.github.actionguard.im.model.ImActionResult;
import io.github.actionguard.im.model.ImGroupMessageSendRequest;
import io.github.actionguard.im.sender.ImGroupMessageSender;

import java.util.List;

public class ImGroupMessageSendActionStepHandler extends AbstractImActionStepHandler implements ActionStepHandler {

    public static final String STEP_TYPE = "IM_GROUP_MESSAGE_SEND";

    private final List<ImGroupMessageSender> senders;

    public ImGroupMessageSendActionStepHandler(List<ImGroupMessageSender> senders) {
        this.senders = List.copyOf(senders);
    }

    @Override
    public String stepType() {
        return STEP_TYPE;
    }

    @Override
    public StepExecutionResult execute(ActionStepContext context) {
        try {
            ImGroupMessageSender sender = resolveSender(providerKey(context));
            ImActionResult result = sender.send(new ImGroupMessageSendRequest(
                    context.actionName(),
                    context.bizKey(),
                    context.stepName(),
                    context.target(),
                    requiredString(context.payload(), "groupId"),
                    requiredString(context.payload(), "messageType"),
                    requiredString(context.payload(), "content"),
                    map(context.payload(), "metadata")
            ));
            return toStepResult(result);
        } catch (IllegalArgumentException ex) {
            return StepExecutionResult.failed("IM_REQUEST_INVALID", ex.getMessage());
        }
    }

    private ImGroupMessageSender resolveSender(String provider) {
        return senders.stream()
                .filter(sender -> provider.equals(sender.provider()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no im message provider registered for target: " + provider));
    }

    private StepExecutionResult toStepResult(ImActionResult result) {
        return result.success() ? StepExecutionResult.succeeded() : StepExecutionResult.failed(result.errorCode(), result.errorMessage());
    }
}
