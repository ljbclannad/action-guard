package io.github.actionguard.notify;

import io.github.actionguard.api.runtime.ActionStepContext;

import java.util.List;
import java.util.Map;

abstract class AbstractNotifyActionStepHandler {

    protected List<String> requiredStringList(Map<String, Object> payload, String fieldName) {
        Object value = payload.get(fieldName);
        if (!(value instanceof List<?> rawList) || rawList.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        return rawList.stream()
                .map(String::valueOf)
                .filter(item -> !item.isBlank())
                .toList();
    }

    protected String optionalString(Map<String, Object> payload, String fieldName) {
        Object value = payload.get(fieldName);
        return value == null ? null : String.valueOf(value);
    }

    protected String requiredString(Map<String, Object> payload, String fieldName) {
        String value = optionalString(payload, fieldName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> variables(Map<String, Object> payload) {
        Object value = payload.get("variables");
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    protected String providerKey(ActionStepContext context) {
        if (context.target() == null || context.target().isBlank()) {
            throw new IllegalArgumentException("target must not be blank");
        }
        return context.target();
    }
}
