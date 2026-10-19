package com.ppln.automation.api.client;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Outcome of one HTTP call. Header names are stored lower-cased; {@code body} is the typed view of
 * {@code rawBody} (null for empty bodies or {@code Void} targets).
 */
public record ApiResponse<T>(
        int status, Map<String, String> headers, String rawBody, T body, long elapsedMs) {

    public ApiResponse(int status, Map<String, String> headers, String rawBody, T body) {
        this(status, headers, rawBody, body, 0L);
    }

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    public Optional<String> header(String name) {
        return Optional.ofNullable(headers.get(name.toLowerCase(Locale.ROOT)));
    }
}
