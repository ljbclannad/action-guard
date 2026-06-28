package io.github.actionguard.core.runtime.retry;

import io.github.actionguard.api.runtime.ActionRetryAction;
import io.github.actionguard.api.runtime.ActionRetryContext;
import io.github.actionguard.api.spi.ActionRetryPolicy;

public class FixedAttemptActionRetryPolicy implements ActionRetryPolicy {

    private final int maxRetryCount;

    public FixedAttemptActionRetryPolicy(int maxRetryCount) {
        if (maxRetryCount < 0) {
            throw new IllegalArgumentException("maxRetryCount must be greater than or equal to 0");
        }
        this.maxRetryCount = maxRetryCount;
    }

    @Override
    public ActionRetryAction decide(Throwable throwable, ActionRetryContext context) {
        int effectiveMaxRetryCount = Math.min(maxRetryCount, context.maxRetryCount());
        return context.retryable() && context.currentRetryCount() < effectiveMaxRetryCount
                ? ActionRetryAction.IMMEDIATE_RETRY
                : ActionRetryAction.DEAD;
    }
}
