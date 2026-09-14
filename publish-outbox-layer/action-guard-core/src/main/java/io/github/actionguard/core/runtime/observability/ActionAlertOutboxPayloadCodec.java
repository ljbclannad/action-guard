package io.github.actionguard.core.runtime.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 告警详情在可靠 Outbox 中的确定性 JSON 编解码。
 */
final class ActionAlertOutboxPayloadCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, String>> DETAILS_TYPE = new TypeReference<>() {
    };

    private ActionAlertOutboxPayloadCodec() {
    }

    static String serialize(Map<String, String> details) {
        try {
            return OBJECT_MAPPER.writeValueAsString(normalize(details));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize action alert details", exception);
        }
    }

    private static Map<String, String> normalize(Map<String, String> details) {
        if (details == null || details.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        details.forEach((key, value) -> normalized.put(
                Objects.requireNonNull(key, "action alert detail key must not be null"),
                Objects.requireNonNull(value, "action alert detail value must not be null")
        ));
        return normalized;
    }

    static Map<String, String> deserialize(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> details = OBJECT_MAPPER.readValue(detailsJson, DETAILS_TYPE);
            return Map.copyOf(details);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to deserialize action alert details", exception);
        }
    }
}
