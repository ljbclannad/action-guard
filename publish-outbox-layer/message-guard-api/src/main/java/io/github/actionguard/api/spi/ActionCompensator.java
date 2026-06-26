package io.github.actionguard.api.spi;

import io.github.actionguard.api.runtime.ActionCompensationContext;
import io.github.actionguard.api.runtime.ActionCompensationResult;

public interface ActionCompensator {

    ActionCompensationResult compensate(ActionCompensationContext context);
}
