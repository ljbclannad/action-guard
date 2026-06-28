package io.github.actionguard.im;

import io.github.actionguard.api.runtime.ActionStepContext;

import java.util.List;
import java.util.Map;

abstract class AbstractImActionStepHandler {

    protected String providerKey(ActionStepContext context) {
        if (context.target() == null || context.target().isBlank()) {
            throw new IllegalArgumentException("target must not be blank");
        }
        return context.target();
    }

    protected String requiredString(Map<String, Object> payload, String fieldName) {
        Object value = payload.get(fieldName);
        String text = value == null ? null : String.valueOf(value);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }

    protected String optionalString(Map<String, Object> payload, String fieldName) {
        Object value = payload.get(fieldName);
        return value == null ? null : String.valueOf(value);
    }

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

    @SuppressWarnings("unchecked")
    protected Map<String, Object> map(Map<String, Object> payload, String fieldName) {
        Object value = payload.get(fieldName);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
