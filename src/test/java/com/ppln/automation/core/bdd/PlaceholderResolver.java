package com.ppln.automation.core.bdd;

import com.ppln.automation.core.config.ConfigManager;
import com.ppln.automation.core.utils.DateUtils;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The single place where {@code {placeholders}} become values, shared by UI and API glue.
 * Built-ins: {@code {baseUrl}} (API), {@code {uiBaseUrl}}, {@code {now}}, {@code {now+1d}}, {@code
 * {uniqueEmail}}, {@code {uniqueString}}, {@code {uuid}}, {@code {string of N chars}}; anything
 * else is a variable stored earlier in the scenario. Generated values are cached in the scenario's
 * variable map so the same placeholder resolves identically within one scenario. {@code
 * {authToken}} is deliberately not resolved here: tokens are applied as headers and never pass
 * through string substitution.
 */
public final class PlaceholderResolver {

    private static final Pattern TOKEN =
            Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9]*)((?:[+-]\\d+[dhm])?)\\}");
    private static final Pattern STRING_OF = Pattern.compile("\\{string of (\\d+) chars\\}");

    private final Map<String, String> variables;

    /**
     * @param scenarioVariables the scenario's mutable variable map (owned by the scenario context)
     */
    public PlaceholderResolver(Map<String, String> scenarioVariables) {
        this.variables = scenarioVariables;
    }

    public String resolve(String input) {
        if (input == null) {
            return null;
        }
        String s =
                STRING_OF.matcher(input).replaceAll(m -> "x".repeat(Integer.parseInt(m.group(1))));
        Matcher m = TOKEN.matcher(s);
        var out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(value(m.group(1), m.group(2))));
        }
        m.appendTail(out);
        return out.toString();
    }

    private String value(String name, String offset) {
        return switch (name) {
            case "baseUrl" -> ConfigManager.config().apiBaseUrl();
            case "uiBaseUrl" -> ConfigManager.config().baseUrl();
            case "now" ->
                    DateTimeFormatter.ISO_INSTANT.format(DateUtils.shift(Instant.now(), offset));
            // example.com, not example.test: both are reserved (RFC 2606, never deliverable), but
            // python's email_validator (behind WTForms' Email()) rejects special-use TLDs like
            // .test outright - confirmed against PPLN's user form 2026-10-17.
            case "uniqueEmail" -> cached(name, () -> "qa+" + shortId() + "@example.com");
            case "uniqueString" -> cached(name, () -> "qa-" + shortId());
            case "uuid" -> cached(name, () -> UUID.randomUUID().toString());
            default -> {
                String v = variables.get(name);
                if (v == null) {
                    throw new IllegalStateException(
                            "Unknown placeholder {%s}. Known: baseUrl, uiBaseUrl, now, uniqueEmail, uniqueString, uuid, or a stored variable %s"
                                    .formatted(name, variables.keySet()));
                }
                yield v;
            }
        };
    }

    private String cached(String key, Supplier<String> generator) {
        return variables.computeIfAbsent(key, k -> generator.get());
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
