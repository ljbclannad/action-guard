package io.github.actionguard.ops.api.support;

public class OperatorResolver {

    public String resolve(String operatorHeader) {
        return operatorHeader == null || operatorHeader.isBlank() ? "anonymous" : operatorHeader;
    }
}
