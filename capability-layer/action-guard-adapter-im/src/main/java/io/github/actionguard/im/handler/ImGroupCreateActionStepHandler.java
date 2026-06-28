package io.github.actionguard.im.handler;

import io.github.actionguard.api.runtime.ActionStepContext;
import io.github.actionguard.api.runtime.StepExecutionResult;
import io.github.actionguard.api.spi.ActionStepHandler;
import io.github.actionguard.im.model.ImActionResult;
import io.github.actionguard.im.model.ImGroupCreateRequest;
import io.github.actionguard.im.sender.ImGroupCreateSender;

import java.util.List;

public class ImGroupCreateActionStepHandler extends AbstractImActionStepHandler implements ActionStepHandler {

    public static final String STEP_TYPE = "IM_GROUP_CREATE";

    private final List<ImGroupCreateSender> senders;

    public ImGroupCreateActionStepHandler(List<ImGroupCreateSender> senders) {
        this.senders = List.copyOf(senders);
    }

    @Override
    public String stepType() {
        return STEP_TYPE;
    }

    @Override
    public StepExecutionResult execute(ActionStepContext context) {
        try {
            ImGroupCreateSender sender = resolveSender(providerKey(context));
            ImActionResult result = sender.create(new ImGroupCreateRequest(
                    context.actionName(),
                    context.bizKey(),
                    context.stepName(),
                    context.target(),
                    requiredString(context.payload(), "groupName"),
                    requiredString(context.payload(), "owner"),
                    requiredStringList(context.payload(), "members"),
                    optionalString(context.payload(), "avatar"),
                    map(context.payload(), "metadata")
            ));
            return toStepResult(result);
        } catch (IllegalArgumentException ex) {
            return StepExecutionResult.failed("IM_REQUEST_INVALID", ex.getMessage());
        }
    }

    private ImGroupCreateSender resolveSender(String provider) {
        return senders.stream()
                .filter(sender -> provider.equals(sender.provider()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no im create provider registered for target: " + provider));
    }

    private StepExecutionResult toStepResult(ImActionResult result) {
        return result.success() ? StepExecutionResult.succeeded() : StepExecutionResult.failed(result.errorCode(), result.errorMessage());
    }
}
