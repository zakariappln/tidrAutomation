package com.ppln.automation.api.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders one request as a copy-pasteable {@code curl} command for bug reports. The bearer token is
 * never written: the command reads it from {@code $USBA_TOKEN}, so the report stays shareable and a
 * reader replays the call as themselves.
 */
public final class CurlCommand {

    public static final String TOKEN_VAR = "USBA_TOKEN";
    private static final String CONTINUATION = " \\" + System.lineSeparator() + "  ";

    private CurlCommand() {}

    /**
     * @param defaultHeaders headers the request context adds to every call (tenant headers)
     * @param body JSON or raw body sent, or null; ignored when {@code spec.multipart()} is set
     */
    public static String render(
            String method,
            String baseUrl,
            String path,
            Map<String, String> defaultHeaders,
            RequestSpec spec,
            String body) {
        Map<String, String> headers = mergedHeaders(defaultHeaders, spec.headers());
        boolean multipart = spec.multipart() != null;
        if (multipart) {
            headers.keySet()
                    .removeIf(k -> k.equalsIgnoreCase("Content-Type")); // curl writes the boundary
        } else if (body != null
                && headers.keySet().stream().noneMatch(k -> k.equalsIgnoreCase("Content-Type"))) {
            headers.put("Content-Type", "application/json");
        }

        var parts = new ArrayList<String>();
        parts.add(
                "curl -i -X "
                        + method.toUpperCase(Locale.ROOT)
                        + " "
                        + quote(url(baseUrl, path, spec.query())));
        headers.forEach((name, value) -> parts.add("-H " + header(name, value)));
        if (multipart) {
            spec.multipart().forEach(f -> parts.add("-F " + quote(formPart(f))));
        } else if (body != null) {
            parts.add("--data-raw " + quote(body));
        }

        var out = new StringBuilder();
        out.append("# export ")
                .append(TOKEN_VAR)
                .append(
                        "=<JWT: copy the Authorization header value (without 'Bearer ') from any request in the browser Network tab>")
                .append(System.lineSeparator());
        if (multipart && spec.multipart().stream().anyMatch(MultipartField::isFile)) {
            out.append(
                            "# file parts are read from src/test/resources/testdata/files/ - run from there or adjust the @paths")
                    .append(System.lineSeparator());
        }
        return out.append(String.join(CONTINUATION, parts)).toString();
    }

    static String url(String baseUrl, String path, Map<String, String> query) {
        String base = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        String full =
                path.matches("(?i)^https?://.*")
                        ? path
                        : base + (path.startsWith("/") ? path : "/" + path);
        if (query.isEmpty()) {
            return full;
        }
        String qs =
                query.entrySet().stream()
                        .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                        .collect(Collectors.joining("&"));
        return full + (full.contains("?") ? "&" : "?") + qs;
    }

    /** Single-quoted for the shell; embedded single quotes become {@code '\''}. */
    static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static Map<String, String> mergedHeaders(
            Map<String, String> defaults, Map<String, String> explicit) {
        var merged = new LinkedHashMap<String, String>();
        defaults.forEach(merged::put);
        explicit.forEach(
                (name, value) -> {
                    merged.keySet()
                            .removeIf(
                                    k ->
                                            k.equalsIgnoreCase(
                                                    name)); // request header overrides context
                    // default
                    merged.put(name, value);
                });
        return merged;
    }

    private static String header(String name, String value) {
        if (name.equalsIgnoreCase("Authorization")
                && value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "\""
                    + name
                    + ": Bearer ${"
                    + TOKEN_VAR
                    + "}\""; // double quotes so the shell expands it
        }
        return quote(name + ": " + value);
    }

    private static String formPart(MultipartField field) {
        return field.isFile()
                ? "%s=@%s;type=%s".formatted(field.name(), field.fileName(), field.mimeType())
                : field.name() + "=" + field.value();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Used by tests: the individual {@code -H}, {@code -F} and body parts, in order. */
    static List<String> lines(String rendered) {
        return List.of(rendered.split(" \\\\" + System.lineSeparator() + " {2}"));
    }
}
