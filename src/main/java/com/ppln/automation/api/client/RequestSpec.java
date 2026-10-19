package com.ppln.automation.api.client;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable request description. {@code body} is serialised with Jackson; {@code rawBody} is sent
 * verbatim (malformed-JSON scenarios must reach the server untouched) and wins when both are set.
 * {@code multipart} (null unless set) sends {@code multipart/form-data} and replaces any body.
 */
public record RequestSpec(
        Map<String, String> headers,
        Map<String, String> query,
        Object body,
        String rawBody,
        List<MultipartField> multipart) {

    public static RequestSpec empty() {
        return new RequestSpec(Map.of(), Map.of(), null, null, null);
    }

    public RequestSpec withHeader(String name, String value) {
        var h = new HashMap<>(headers);
        h.put(name, value);
        return new RequestSpec(Map.copyOf(h), query, body, rawBody, multipart);
    }

    public RequestSpec withHeaders(Map<String, String> extra) {
        var h = new HashMap<>(headers);
        h.putAll(extra);
        return new RequestSpec(Map.copyOf(h), query, body, rawBody, multipart);
    }

    /** Removes a header case-insensitively (e.g. to send a request without Content-Type). */
    public RequestSpec withoutHeader(String name) {
        var h = new HashMap<>(headers);
        h.keySet().removeIf(k -> k.equalsIgnoreCase(name));
        return new RequestSpec(Map.copyOf(h), query, body, rawBody, multipart);
    }

    public RequestSpec withQuery(String name, String value) {
        var q = new HashMap<>(query);
        q.put(name, value);
        return new RequestSpec(headers, Map.copyOf(q), body, rawBody, multipart);
    }

    public RequestSpec withBody(Object value) {
        return new RequestSpec(headers, query, value, null, null);
    }

    public RequestSpec withRawBody(String raw) {
        return new RequestSpec(headers, query, null, raw, null);
    }

    /**
     * Multipart form body. Any explicit Content-Type header is dropped: Playwright must write it
     * itself so the boundary parameter is correct.
     */
    public RequestSpec withMultipart(List<MultipartField> fields) {
        return new RequestSpec(
                withoutHeader("Content-Type").headers(), query, null, null, List.copyOf(fields));
    }

    public boolean hasHeader(String name) {
        return headers.keySet().stream()
                .anyMatch(k -> k.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)));
    }
}
