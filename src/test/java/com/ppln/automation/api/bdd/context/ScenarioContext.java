package com.ppln.automation.api.bdd.context;

import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.Playwright;
import com.ppln.automation.api.auth.AuthHeaders;
import com.ppln.automation.api.client.ApiClient;
import com.ppln.automation.api.client.ApiResponse;
import com.ppln.automation.api.client.RequestSpec;
import com.ppln.automation.core.bdd.PlaceholderResolver;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Per-scenario API state. Created by PicoContainer; never shared across scenarios. */
public final class ScenarioContext implements AutoCloseable {

    private final Map<String, String> variables = new HashMap<>();
    private final Deque<Runnable> cleanups = new ArrayDeque<>();
    private final PlaceholderResolver resolver = new PlaceholderResolver(variables);
    private final Map<Map<String, String>, APIRequestContext> contexts = new HashMap<>();

    private Playwright playwright;
    private RequestSpec pendingSpec = RequestSpec.empty();
    private String authRole; // null = unauthenticated
    private boolean expiredToken;
    private boolean multipartRequested;
    private ApiResponse<Map<String, Object>> lastResponse;
    private String lastMethod;
    private String lastPath;
    private long lastElapsedMs;

    public PlaceholderResolver resolver() {
        return resolver;
    }

    public String resolve(String text) {
        return resolver.resolve(text);
    }

    // ---- variables (`store "$.id" as "branchId"`) ----
    public void put(String key, String value) {
        variables.put(key, value);
    }

    public String get(String key) {
        String v = variables.get(key);
        if (v == null) {
            throw new IllegalStateException(
                    "No stored variable {" + key + "}. Stored: " + variables.keySet());
        }
        return v;
    }

    public String variablesForReport() {
        return variables.entrySet().stream()
                .filter(e -> !e.getKey().startsWith("__"))
                .map(e -> e.getKey() + " = " + e.getValue())
                .collect(Collectors.joining(System.lineSeparator()));
    }

    // ---- pending request ----
    public RequestSpec spec() {
        return pendingSpec;
    }

    public void spec(RequestSpec spec) {
        pendingSpec = spec;
    }

    public void resetRequest() {
        pendingSpec = RequestSpec.empty();
        multipartRequested = false;
    }

    /** The next send builds a multipart body from the endpoint's form-field fixture. */
    public void requestMultipart() {
        multipartRequested = true;
    }

    public boolean multipartRequested() {
        return multipartRequested;
    }

    public void authRole(String role) {
        authRole = role;
        expiredToken = false;
    }

    public void unauthenticated() {
        authRole = null;
        expiredToken = false;
    }

    public void expiredToken() {
        authRole = null;
        expiredToken = true;
    }

    public Optional<String> authRole() {
        return Optional.ofNullable(authRole);
    }

    public boolean useExpiredToken() {
        return expiredToken;
    }

    // ---- last response ----
    public void record(String method, String path, ApiResponse<Map<String, Object>> response) {
        record(method, path, response, response.elapsedMs());
    }

    public void record(
            String method, String path, ApiResponse<Map<String, Object>> response, long elapsedMs) {
        lastMethod = method;
        lastPath = path;
        lastResponse = response;
        lastElapsedMs = elapsedMs;
    }

    public ApiResponse<Map<String, Object>> lastResponse() {
        if (lastResponse == null) {
            throw new IllegalStateException("No request has been sent yet in this scenario");
        }
        return lastResponse;
    }

    /** For failure messages: {@code GET /api/Branch/GetAll}. */
    public String lastCall() {
        return lastMethod + " " + lastPath;
    }

    public String lastPath() {
        if (lastPath == null) {
            throw new IllegalStateException("No request has been sent yet in this scenario");
        }
        return lastPath;
    }

    public long lastElapsedMs() {
        return lastElapsedMs;
    }

    // ---- transport ----
    /** Default context: tenant headers only; Authorization is added per request by RequestSteps. */
    public APIRequestContext request() {
        return requestFor(AuthHeaders.tenant());
    }

    /** One cached {@link APIRequestContext} per distinct default-header set within the scenario. */
    public APIRequestContext requestFor(Map<String, String> defaultHeaders) {
        return contexts.computeIfAbsent(
                Map.copyOf(defaultHeaders), h -> ApiClient.newContext(playwright(), h));
    }

    private Playwright playwright() {
        if (playwright == null) {
            playwright = Playwright.create();
        }
        return playwright;
    }

    // ---- cleanup (LIFO) ----
    public void onCleanup(Runnable action) {
        cleanups.push(action);
    }

    public Deque<Runnable> cleanups() {
        return cleanups;
    }

    @Override
    public void close() {
        contexts.values().forEach(APIRequestContext::dispose);
        contexts.clear();
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }
}
