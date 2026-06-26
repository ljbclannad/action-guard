package io.github.actionguard.api.spi;

import io.github.actionguard.api.runtime.ActionRetryAction;
import io.github.actionguard.api.runtime.ActionRetryContext;

public interface ActionRetryPolicy {

    ActionRetryAction decide(Throwable throwable, ActionRetryContext context);
}
