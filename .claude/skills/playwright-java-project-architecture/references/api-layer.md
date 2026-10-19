# API Layer

Built on Playwright's `APIRequestContext` so UI and API share one runtime, one config, one auth story, and one Allure attachment path. Do not add RestAssured unless the user explicitly asks; if they do, keep the same `ApiClient` contract and swap the transport.

## ApiResponse and RequestSpec records

```java
package com.company.automation.api.client;

import java.util.Map;
import java.util.Optional;

public record ApiResponse<T>(int status, Map<String, String> headers, String rawBody, T body) {
    public boolean isSuccess()          { return status >= 200 && status < 300; }
    public Optional<String> header(String name) { return Optional.ofNullable(headers.get(name.toLowerCase())); }
}

public record RequestSpec(Map<String, String> headers, Map<String, String> query, Object body) {
    public static RequestSpec empty() { return new RequestSpec(Map.of(), Map.of(), null); }
    public RequestSpec withHeader(String k, String v) { var h = new java.util.HashMap<>(headers); h.put(k, v); return new RequestSpec(h, query, body); }
    public RequestSpec withQuery(String k, String v)  { var q = new java.util.HashMap<>(query);  q.put(k, v); return new RequestSpec(headers, q, body); }
    public RequestSpec withBody(Object b)             { return new RequestSpec(headers, query, b); }
}
```

## ApiClient (base)

```java
package com.company.automation.api.client;

import com.company.automation.core.config.ConfigManager;
import com.company.automation.core.reporting.AllureAttachments;
import com.company.automation.core.utils.JsonUtils;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RequestOptions;
import io.qameta.allure.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

public abstract class ApiClient {

    private static final Logger log = LoggerFactory.getLogger(ApiClient.class);
    protected final APIRequestContext request;

    protected ApiClient(APIRequestContext request) { this.request = request; }

    /** Factory used by BaseApiTest; one context per test class, per thread. */
    public static APIRequestContext newContext(Playwright pw, Map<String, String> defaultHeaders) {
        var cfg = ConfigManager.config();
        return pw.request().newContext(new APIRequest.NewContextOptions()
            .setBaseURL(cfg.apiBaseUrl())
            .setTimeout(cfg.apiTimeoutMs())
            .setExtraHTTPHeaders(defaultHeaders));
    }

    protected <T> ApiResponse<T> get(String path, RequestSpec spec, Class<T> type) {
        return execute("GET", path, spec, type);
    }
    protected <T> ApiResponse<T> post(String path, RequestSpec spec, Class<T> type) {
        return execute("POST", path, spec, type);
    }
    protected <T> ApiResponse<T> put(String path, RequestSpec spec, Class<T> type) {
        return execute("PUT", path, spec, type);
    }
    protected <T> ApiResponse<T> patch(String path, RequestSpec spec, Class<T> type) {
        return execute("PATCH", path, spec, type);
    }
    protected <T> ApiResponse<T> delete(String path, RequestSpec spec, Class<T> type) {
        return execute("DELETE", path, spec, type);
    }

    @Step("{method} {path}")
    private <T> ApiResponse<T> execute(String method, String path, RequestSpec spec, Class<T> type) {
        var options = RequestOptions.create().setMethod(method);
        spec.headers().forEach(options::setHeader);
        spec.query().forEach(options::setQueryParam);
        String requestJson = null;
        if (spec.body() != null) {
            requestJson = JsonUtils.toJson(spec.body());
            options.setData(requestJson).setHeader("Content-Type", "application/json");
        }
        AllureAttachments.text("Request %s %s".formatted(method, path),
            "headers=%s%nquery=%s%n%n%s".formatted(spec.headers(), spec.query(), requestJson == null ? "" : requestJson));

        long started = System.nanoTime();
        APIResponse resp = request.fetch(path, options);
        long ms = (System.nanoTime() - started) / 1_000_000;

        String raw = resp.text();
        log.info("{} {} -> {} in {} ms", method, path, resp.status(), ms);
        AllureAttachments.json("Response %d (%d ms)".formatted(resp.status(), ms), raw);

        T parsed = (type == Void.class || raw.isBlank()) ? null : JsonUtils.fromJson(raw, type);
        return new ApiResponse<>(resp.status(), lowerCaseKeys(resp.headers()), raw, parsed);
    }

    private static Map<String, String> lowerCaseKeys(Map<String, String> h) {
        var out = new java.util.HashMap<String, String>();
        h.forEach((k, v) -> out.put(k.toLowerCase(), v));
        return Map.copyOf(out);
    }
}
```

Design notes:
- Every call is one Allure step with request + response attached — a failing API test is fully diagnosable from the report.
- Deserialisation is typed via `Class<T>`; use `JsonUtils.fromJson(raw, new TypeReference<List<UserResponse>>(){})` overload for collections.
- Parsing failures throw immediately with the raw body in the message; that is a real defect signal, not something to swallow.

## Resource client

```java
package com.company.automation.api.clients;

import com.company.automation.api.client.*;
import com.company.automation.api.models.request.CreateUserRequest;
import com.company.automation.api.models.response.UserResponse;
import com.microsoft.playwright.APIRequestContext;

public final class UsersClient extends ApiClient {

    private static final String USERS = "/v1/users";

    public UsersClient(APIRequestContext request) { super(request); }

    public ApiResponse<UserResponse> create(CreateUserRequest body) {
        return post(USERS, RequestSpec.empty().withBody(body), UserResponse.class);
    }

    public ApiResponse<UserResponse> getById(String id) {
        return get(USERS + "/" + id, RequestSpec.empty(), UserResponse.class);
    }

    public ApiResponse<Void> delete(String id) {
        return delete(USERS + "/" + id, RequestSpec.empty(), Void.class);
    }
}
```

Models are records with Jackson annotations only when the JSON name differs:

```java
public record CreateUserRequest(String email, String fullName, String role) {}
public record UserResponse(String id, String email, @JsonProperty("full_name") String fullName, String role, Instant createdAt) {}
```

## TokenProvider

`api/auth/TokenProvider` obtains a bearer token once per JVM (or per role) via the auth endpoint, caches it with expiry, and `BaseApiTest` injects it as a default header. Credentials come from `ConfigManager` (env vars in CI). Never log tokens; `AllureAttachments` must mask `Authorization` headers.

## BaseApiTest

```java
package com.company.automation.api.base;

import com.company.automation.api.auth.TokenProvider;
import com.company.automation.api.client.ApiClient;
import com.company.automation.core.listeners.TestListener;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.Playwright;
import org.testng.annotations.*;
import java.util.Map;

@Listeners(TestListener.class)
public abstract class BaseApiTest {

    private Playwright playwright;
    protected APIRequestContext request;

    @BeforeClass(alwaysRun = true)
    public void createRequestContext() {
        playwright = Playwright.create();
        request = ApiClient.newContext(playwright, Map.of(
            "Accept", "application/json",
            "Authorization", "Bearer " + TokenProvider.token()));
    }

    @AfterClass(alwaysRun = true)
    public void dispose() {
        if (request != null) request.dispose();
        if (playwright != null) playwright.close();
    }
}
```

`parallel="classes"` in `testng-api.xml` therefore gives one context per class per thread with no sharing.

## Sample API test

```java
package com.company.automation.api.tests;

import com.company.automation.api.base.BaseApiTest;
import com.company.automation.api.clients.UsersClient;
import com.company.automation.api.models.request.CreateUserRequest;
import com.company.automation.core.utils.JsonSchemaValidator;
import io.qameta.allure.*;
import org.testng.annotations.*;
import static org.assertj.core.api.Assertions.assertThat;

@Epic("Users API")
@Feature("User lifecycle")
public class UsersApiTest extends BaseApiTest {

    private UsersClient users;
    private String createdId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "createRequestContext")
    public void init() { users = new UsersClient(request); }

    @Test(groups = {"api", "smoke", "users"})
    @Story("Create user")
    @Severity(SeverityLevel.CRITICAL)
    public void shouldCreateUserWhenPayloadIsValid() {
        var req = new CreateUserRequest("api.user+%d@example.com".formatted(System.nanoTime()), "API User", "viewer");
        var resp = users.create(req);

        assertThat(resp.status()).isEqualTo(201);
        JsonSchemaValidator.assertMatches(resp.rawBody(), "schemas/user.schema.json");
        assertThat(resp.body().email()).isEqualTo(req.email());
        assertThat(resp.body().id()).isNotBlank();
        createdId = resp.body().id();
    }

    @Test(groups = {"api", "regression", "users"}, dependsOnMethods = "shouldCreateUserWhenPayloadIsValid")
    @Story("Delete user")
    @Severity(SeverityLevel.NORMAL)
    public void shouldReturn404AfterUserIsDeleted() {
        assertThat(users.delete(createdId).status()).isEqualTo(204);
        assertThat(users.getById(createdId).status()).isEqualTo(404);
    }
}
```

Assertion library for API tests is AssertJ (fluent, readable failures). UI tests use `PlaywrightAssertions` for the DOM and AssertJ for plain values. Never mix TestNG `Assert` in — one style per layer.

## Contract checks

`JsonSchemaValidator` wraps `com.networknt:json-schema-validator`; schemas live in `src/test/resources/schemas/`. Assert schema **before** field values so a shape change is reported as such.
