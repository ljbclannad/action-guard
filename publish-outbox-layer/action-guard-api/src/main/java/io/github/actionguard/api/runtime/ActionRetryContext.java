package io.github.actionguard.api.runtime;

public record ActionRetryContext(
        int currentRetryCount,
        int maxRetryCount,
        boolean retryable
) {

    public boolean canRetry() {
        return retryable && currentRetryCount < maxRetryCount;
    }
}
