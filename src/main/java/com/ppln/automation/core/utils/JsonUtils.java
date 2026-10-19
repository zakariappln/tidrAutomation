package com.ppln.automation.core.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;
import java.util.Optional;

/** Single Jackson configuration for the whole framework (records, java.time, lenient unknowns). */
public final class JsonUtils {

    private static final ObjectMapper MAPPER =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private JsonUtils() {}

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Cannot serialise " + value.getClass().getSimpleName(), e);
        }
    }

    public static String prettyPrint(String json) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(MAPPER.readTree(json));
        } catch (JsonProcessingException e) {
            return json;
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Cannot parse JSON as %s: %s%nBody: %s"
                            .formatted(type.getSimpleName(), e.getOriginalMessage(), json),
                    e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Cannot parse JSON as %s: %s%nBody: %s"
                            .formatted(type.getType(), e.getOriginalMessage(), json),
                    e);
        }
    }

    public static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Body is not valid JSON: " + e.getOriginalMessage(), e);
        }
    }

    /** Lenient parse for generic BDD assertions: empty when the body is not a JSON object. */
    public static Optional<Map<String, Object>> tryParseMap(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            return node.isObject()
                    ? Optional.of(MAPPER.convertValue(node, MAP_TYPE))
                    : Optional.empty();
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
