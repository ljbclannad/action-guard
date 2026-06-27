package io.github.actionguard.im;

import io.github.actionguard.api.runtime.ActionStepContext;
import io.github.actionguard.api.runtime.StepExecutionResult;
import io.github.actionguard.api.spi.ActionStepHandler;

import java.util.List;

public class ImGroupInviteActionStepHandler extends AbstractImActionStepHandler implements ActionStepHandler {

    public static final String STEP_TYPE = "IM_GROUP_INVITE";

    private final List<ImGroupInviteSender> senders;

    public ImGroupInviteActionStepHandler(List<ImGroupInviteSender> senders) {
        this.senders = List.copyOf(senders);
    }

    @Override
    public String stepType() {
        return STEP_TYPE;
    }

    @Override
    public StepExecutionResult execute(ActionStepContext context) {
        try {
            ImGroupInviteSender sender = resolveSender(providerKey(context));
            ImActionResult result = sender.invite(new ImGroupInviteRequest(
                    context.actionName(),
                    context.bizKey(),
                    context.stepName(),
                    context.target(),
                    requiredString(context.payload(), "groupId"),
                    requiredString(context.payload(), "inviter"),
                    requiredStringList(context.payload(), "members")
            ));
            return toStepResult(result);
        } catch (IllegalArgumentException ex) {
            return StepExecutionResult.failed("IM_REQUEST_INVALID", ex.getMessage());
        }
    }

    private ImGroupInviteSender resolveSender(String provider) {
        return senders.stream()
                .filter(sender -> provider.equals(sender.provider()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no im invite provider registered for target: " + provider));
    }

    private StepExecutionResult toStepResult(ImActionResult result) {
        return result.success() ? StepExecutionResult.succeeded() : StepExecutionResult.failed(result.errorCode(), result.errorMessage());
    }
}
