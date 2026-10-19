package com.ppln.automation.api.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Uniform backend response envelope {@code { statusCode, message, data }}. {@code data} may be an
 * object, an array, {@code null}, or a {@link Paged} wrapper.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Envelope<T>(int statusCode, String message, T data) {

    public boolean hasData() {
        return data != null;
    }

    public T dataOrThrow() {
        if (data == null) {
            throw new IllegalStateException(
                    "Envelope has no data (statusCode=%d, message=%s)"
                            .formatted(statusCode, message));
        }
        return data;
    }
}
