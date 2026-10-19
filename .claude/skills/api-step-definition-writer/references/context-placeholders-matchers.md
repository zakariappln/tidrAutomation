# ScenarioContext, PlaceholderResolver, ExpectedValueMatcher, SchemaRegistry

All four are plain classes. PicoContainer instantiates `ScenarioContext` once per scenario and injects it into every step class constructor that declares it — no annotations, no static access.

## ScenarioContext

```java
package com.company.automation.api.bdd.context;

import com.company.automation.api.client.ApiClient;
import com.company.automation.api.client.ApiResponse;
import com.company.automation.api.client.RequestSpec;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.Playwright;
import java.util.*;

/** Per-scenario state. Created by PicoContainer; never share across scenarios. */
public final class ScenarioContext implements AutoCloseable {

    private final Playwright playwright = Playwright.create();
    private final Map<String, String> variables = new HashMap<>();
    private final Deque<Runnable> cleanups = new ArrayDeque<>();
    private final PlaceholderResolver resolver = new PlaceholderResolver(variables);

    private APIRequestContext request;
    private RequestSpec pendingSpec = RequestSpec.empty();
    private String authRole;                       // null = unauthenticated
    private boolean expiredToken;
    private ApiResponse<Map<String, Object>> lastResponse;
    private String lastMethod;
    private String lastPath;

    public PlaceholderResolver resolver()       { return resolver; }
    public String resolve(String s)             { return resolver.resolve(s); }

    // ---- variables (`store "$.id" as "userId"`) ----
    public void put(String key, String value)   { variables.put(key, value); }
    public String get(String key) {
        var v = variables.get(key);
        if (v == null) throw new IllegalStateException("No stored variable {" + key + "}. Stored: " + variables.keySet());
        return v;
    }

    // ---- pending request ----
    public RequestSpec spec()                   { return pendingSpec; }
    public void spec(RequestSpec s)             { pendingSpec = s; }
    public void authRole(String role)           { authRole = role; expiredToken = false; }
    public void unauthenticated()               { authRole = null; expiredToken = false; }
    public void expiredToken()                  { authRole = null; expiredToken = true; }
    public Optional<String> authRole()          { return Optional.ofNullable(authRole); }
    public boolean useExpiredToken()            { return expiredToken; }
    public void resetRequest()                  { pendingSpec = RequestSpec.empty(); }

    // ---- last response ----
    public void record(String method, String path, ApiResponse<Map<String, Object>> r) {
        lastMethod = method; lastPath = path; lastResponse = r;
    }
    public ApiResponse<Map<String, Object>> lastResponse() {
        if (lastResponse == null) throw new IllegalStateException("No request has been sent yet in this scenario");
        return lastResponse;
    }
    /** For failure messages: "POST /v1/users". */
    public String lastCall()                    { return lastMethod + " " + lastPath; }

    // ---- transport ----
    public APIRequestContext request() {
        if (request == null) request = ApiClient.newContext(playwright, Map.of());
        return request;
    }

    // ---- cleanup (LIFO) ----
    public void onCleanup(Runnable r)           { cleanups.push(r); }
    public Deque<Runnable> cleanups()           { return cleanups; }

    @Override public void close() {
        if (request != null) request.dispose();
        playwright.close();
    }
}
```

Why `Map<String,Object>` for the body type: generic response steps assert by JsonPath on the raw string; the typed record path is for plain TestNG tests, not BDD glue.

## PlaceholderResolver

```java
package com.company.automation.api.bdd.context;

import com.company.automation.core.config.ConfigManager;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Single place where {placeholders} become values. Generated values are cached per scenario. */
public final class PlaceholderResolver {

    private static final Pattern TOKEN = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9]*)((?:[+-]\\d+[dhm])?)\\}");
    private static final Pattern STRING_OF = Pattern.compile("\\{string of (\\d+) chars\\}");

    private final Map<String, String> variables;

    public PlaceholderResolver(Map<String, String> scenarioVariables) { this.variables = scenarioVariables; }

    public String resolve(String input) {
        if (input == null) return null;
        var s = STRING_OF.matcher(input).replaceAll(m -> "x".repeat(Integer.parseInt(m.group(1))));
        var m = TOKEN.matcher(s);
        var out = new StringBuilder();
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(value(m.group(1), m.group(2))));
        m.appendTail(out);
        return out.toString();
    }

    private String value(String name, String offset) {
        return switch (name) {
            case "baseUrl"      -> ConfigManager.config().apiBaseUrl();
            case "now"          -> DateTimeFormatter.ISO_INSTANT.format(shift(Instant.now(), offset));
            case "uniqueEmail"  -> cached(name, () -> "qa+" + shortId() + "@example.test");
            case "uniqueString" -> cached(name, () -> "qa-" + shortId());
            case "uuid"         -> cached(name, () -> UUID.randomUUID().toString());
            default             -> {
                var v = variables.get(name);
                if (v == null) throw new IllegalStateException(
                    "Unknown placeholder {" + name + "}. Known: baseUrl, now, uniqueEmail, uniqueString, uuid, or a stored variable " + variables.keySet());
                yield v;
            }
        };
    }

    private String cached(String key, java.util.function.Supplier<String> gen) {
        return variables.computeIfAbsent(key, k -> gen.get());
    }

    private static Instant shift(Instant base, String offset) {
        if (offset.isEmpty()) return base;
        long n = Long.parseLong(offset.substring(0, offset.length() - 1));
        return switch (offset.charAt(offset.length() - 1)) {
            case 'd' -> base.plus(Duration.ofDays(n));
            case 'h' -> base.plus(Duration.ofHours(n));
            default  -> base.plus(Duration.ofMinutes(n));
        };
    }

    private static String shortId() { return UUID.randomUUID().toString().substring(0, 8); }
}
```

`{authToken}` is deliberately **not** resolved here — auth is applied by `RequestSteps` via `TokenProvider` so tokens never pass through string substitution or reach an attachment.

## ExpectedValueMatcher

```java
package com.company.automation.api.bdd.context;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/** Closed set of matchers from the Gherkin style guide. Returns null when matched, else a failure reason. */
public final class ExpectedValueMatcher {

    public static final Object ABSENT = new Object();   // sentinel: key does not exist

    private ExpectedValueMatcher() {}

    public static String check(Object actual, String expected) {
        var e = expected.trim();
        if (e.equals("is absent"))        return actual == ABSENT ? null : "expected key to be absent but was <" + actual + ">";
        if (actual == ABSENT)             return "key is absent";
        if (e.equals("is null"))          return actual == null ? null : "expected null but was <" + actual + ">";
        if (e.equals("is not empty"))     return actual != null && !actual.toString().isBlank() && !(actual instanceof Collection<?> c && c.isEmpty()) ? null : "expected non-empty but was <" + actual + ">";
        if (e.equals("is a UUID"))        return tryParse(() -> UUID.fromString(str(actual)), "UUID", actual);
        if (e.equals("is an ISO-8601 timestamp")) return tryParse(() -> Instant.parse(str(actual)), "ISO-8601 timestamp", actual);
        if (e.equals("is a positive integer")) return actual instanceof Number n && n.longValue() > 0 && n.doubleValue() % 1 == 0 ? null : "expected positive integer but was <" + actual + ">";
        if (e.startsWith("matches "))     return Pattern.matches(e.substring(8).trim(), str(actual)) ? null : "<" + actual + "> does not match /" + e.substring(8).trim() + "/";
        if (e.startsWith("is one of "))   { var set = Arrays.stream(e.substring(10).split(",")).map(String::trim).toList(); return set.contains(str(actual)) ? null : "<" + actual + "> not in " + set; }
        if (e.startsWith("has size "))    { int n = Integer.parseInt(e.substring(9).trim()); int size = actual instanceof Collection<?> c ? c.size() : actual instanceof Map<?,?> m ? m.size() : -1; return size == n ? null : "expected size " + n + " but was " + size; }
        if (e.startsWith("contains "))    { var needle = unquote(e.substring(9)); boolean ok = actual instanceof Collection<?> c ? c.stream().map(String::valueOf).anyMatch(needle::equals) : str(actual).contains(needle); return ok ? null : "<" + actual + "> does not contain <" + needle + ">"; }
        // literal (numbers/booleans compared by string form so tables can say 400 or true)
        return e.equals(str(actual)) ? null : "expected <" + e + "> but was <" + actual + ">";
    }

    private static String str(Object o) { return String.valueOf(o); }
    private static String unquote(String s) { s = s.trim(); return s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"") ? s.substring(1, s.length() - 1) : s; }
    private static String tryParse(Runnable r, String what, Object actual) {
        try { r.run(); return null; } catch (IllegalArgumentException | DateTimeParseException ex) { return "<" + actual + "> is not a " + what; }
    }
}
```

Unit-test every branch (`ExpectedValueMatcherTest`, TestNG group `unit`). When the style guide gains a matcher, add it here and a test in the same commit.

## SchemaRegistry

```java
package com.company.automation.api.bdd.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Loads each JSON Schema once per JVM (immutable after load ⇒ safe to share). */
public final class SchemaRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final Map<String, JsonSchema> CACHE = new ConcurrentHashMap<>();

    private SchemaRegistry() {}

    /** @return empty string when valid, else one violation per line. */
    public static String validate(String classpathSchema, String json) {
        var schema = CACHE.computeIfAbsent(classpathSchema, SchemaRegistry::load);
        try {
            JsonNode node = MAPPER.readTree(json);
            Set<ValidationMessage> errors = schema.validate(node);
            return errors.stream().map(ValidationMessage::getMessage).sorted().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            return "response body is not valid JSON: " + e.getMessage();
        }
    }

    private static JsonSchema load(String path) {
        try (InputStream in = SchemaRegistry.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Schema not found on classpath: " + path);
            return FACTORY.getSchema(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read schema " + path, e);
        }
    }
}
```

This is the only intentionally static holder in the glue: schemas are immutable, so caching them per JVM is safe and the rule "no static **mutable** state" still holds.
