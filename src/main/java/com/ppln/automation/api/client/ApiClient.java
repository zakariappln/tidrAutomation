package com.ppln.automation.api.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import com.ppln.automation.core.config.ConfigManager;
import com.ppln.automation.core.config.FrameworkConfig;
import com.ppln.automation.core.reporting.AllureAttachments;
import com.ppln.automation.core.utils.JsonUtils;
import io.qameta.allure.Step;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base for resource clients, built on Playwright's {@link APIRequestContext}. Every call is one
 * Allure step with masked request and response attachments, so a failing API test is diagnosable
 * from the report alone. Clients never assert.
 */
public abstract class ApiClient {

    private static final Logger log = LoggerFactory.getLogger(ApiClient.class);
    private static final String JSON = "application/json";

    /**
     * Immutable per-context metadata, weakly keyed so disposed contexts are collected. Not scenario
     * state: written once when a context is created and only read afterwards.
     */
    private static final Map<APIRequestContext, ContextInfo> CONTEXTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    protected final APIRequestContext request;

    protected ApiClient(APIRequestContext request) {
        this.request = request;
    }

    /**
     * Context bound to {@code api.base.url}; one per test class / scenario, never shared across
     * threads.
     */
    public static APIRequestContext newContext(
            Playwright playwright, Map<String, String> defaultHeaders) {
        return newContext(playwright, ConfigManager.config().apiBaseUrl(), defaultHeaders);
    }

    public static APIRequestContext newContext(
            Playwright playwright, String baseUrl, Map<String, String> defaultHeaders) {
        FrameworkConfig cfg = ConfigManager.config();
        APIRequestContext context =
                playwright
                        .request()
                        .newContext(
                                new APIRequest.NewContextOptions()
                                        .setBaseURL(baseUrl)
                                        .setTimeout(cfg.apiTimeoutMs())
                                        .setExtraHTTPHeaders(defaultHeaders));
        CONTEXTS.put(context, new ContextInfo(baseUrl, Map.copyOf(defaultHeaders)));
        return context;
    }

    /** What a context adds to every call, kept so the curl attachment shows the full request. */
    private record ContextInfo(String baseUrl, Map<String, String> defaultHeaders) {}

    public APIRequestContext requestContext() {
        return request;
    }

    protected <T> ApiResponse<T> get(String path, RequestSpec spec, Class<T> type) {
        return execute("GET", path, spec, parser(type));
    }

    protected <T> ApiResponse<T> get(String path, RequestSpec spec, TypeReference<T> type) {
        return execute("GET", path, spec, parser(type));
    }

    protected <T> ApiResponse<T> post(String path, RequestSpec spec, Class<T> type) {
        return execute("POST", path, spec, parser(type));
    }

    protected <T> ApiResponse<T> post(String path, RequestSpec spec, TypeReference<T> type) {
        return execute("POST", path, spec, parser(type));
    }

    protected <T> ApiResponse<T> put(String path, RequestSpec spec, Class<T> type) {
        return execute("PUT", path, spec, parser(type));
    }

    protected <T> ApiResponse<T> put(String path, RequestSpec spec, TypeReference<T> type) {
        return execute("PUT", path, spec, parser(type));
    }

    protected <T> ApiResponse<T> patch(String path, RequestSpec spec, Class<T> type) {
        return execute("PATCH", path, spec, parser(type));
    }

    protected <T> ApiResponse<T> delete(String path, RequestSpec spec, Class<T> type) {
        return execute("DELETE", path, spec, parser(type));
    }

    /**
     * Generic exchange used by the BDD glue: the body is parsed leniently into a map (empty when
     * the body is not a JSON object) because scenarios assert with JsonPath on {@link
     * ApiResponse#rawBody()}.
     */
    public ApiResponse<Map<String, Object>> exchange(String method, String path, RequestSpec spec) {
        return execute(
                method.toUpperCase(Locale.ROOT),
                path,
                spec,
                raw -> JsonUtils.tryParseMap(raw).orElse(Map.of()));
    }

    @Step("{method} {path}")
    protected <T> ApiResponse<T> execute(
            String method, String path, RequestSpec spec, Function<String, T> parser) {
        var options = RequestOptions.create().setMethod(method);
        spec.headers().forEach(options::setHeader);
        spec.query().forEach(options::setQueryParam);

        String requestBody = null;
        if (spec.multipart() != null) {
            options.setMultipart(formData(spec.multipart()));
            requestBody =
                    spec.multipart().stream()
                            .map(MultipartField::describe)
                            .collect(
                                    Collectors.joining(
                                            System.lineSeparator(),
                                            "multipart/form-data" + System.lineSeparator(),
                                            ""));
        } else if (spec.rawBody() != null) {
            requestBody = spec.rawBody();
            options.setData(
                    requestBody.getBytes(
                            StandardCharsets
                                    .UTF_8)); // bytes: Playwright re-quotes non-JSON strings
            if (!spec.hasHeader("Content-Type")) {
                options.setHeader("Content-Type", JSON);
            }
        } else if (spec.body() != null) {
            requestBody = JsonUtils.toJson(spec.body());
            options.setData(requestBody.getBytes(StandardCharsets.UTF_8));
            if (!spec.hasHeader("Content-Type")) {
                options.setHeader("Content-Type", JSON);
            }
        }
        ContextInfo info = CONTEXTS.get(request);
        AllureAttachments.curl(
                "cURL %s %s".formatted(method, path),
                CurlCommand.render(
                        method,
                        info == null ? ConfigManager.config().apiBaseUrl() : info.baseUrl(),
                        path,
                        info == null ? Map.of() : info.defaultHeaders(),
                        spec,
                        spec.multipart() == null ? requestBody : null));
        AllureAttachments.text(
                "Request %s %s".formatted(method, path),
                "headers=%s%nquery=%s%n%n%s"
                        .formatted(
                                spec.headers(),
                                spec.query(),
                                requestBody == null ? "" : requestBody));

        long started = System.nanoTime();
        APIResponse response = request.fetch(path, options);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        String raw = response.text();
        log.info("{} {} -> {} in {} ms", method, path, response.status(), elapsedMs);
        AllureAttachments.json("Response %d (%d ms)".formatted(response.status(), elapsedMs), raw);

        T parsed = raw.isBlank() ? null : parser.apply(raw);
        return new ApiResponse<>(
                response.status(), lowerCaseKeys(response.headers()), raw, parsed, elapsedMs);
    }

    private static FormData formData(List<MultipartField> fields) {
        FormData form = FormData.create();
        for (MultipartField field : fields) {
            if (field.isFile()) {
                form.append(
                        field.name(),
                        new FilePayload(field.fileName(), field.mimeType(), field.content()));
            } else {
                form.append(field.name(), field.value());
            }
        }
        return form;
    }

    private static <T> Function<String, T> parser(Class<T> type) {
        return type == Void.class ? raw -> null : raw -> JsonUtils.fromJson(raw, type);
    }

    private static <T> Function<String, T> parser(TypeReference<T> type) {
        return raw -> JsonUtils.fromJson(raw, type);
    }

    private static Map<String, String> lowerCaseKeys(Map<String, String> headers) {
        var out = new HashMap<String, String>();
        headers.forEach((k, v) -> out.put(k.toLowerCase(Locale.ROOT), v));
        return Map.copyOf(out);
    }
}
