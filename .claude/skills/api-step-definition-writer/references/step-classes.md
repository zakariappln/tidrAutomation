# Step Classes

One method per vocabulary phrase. Cucumber Expressions (`{string}`, `{int}`, `{word}`) preferred over regex. Every string argument passes through `ctx.resolve(...)`. Constructors receive `ScenarioContext` (PicoContainer injection).

## DataTables helper (support)

```java
package com.company.automation.api.bdd.support;

import io.cucumber.datatable.DataTable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DataTables {
    private DataTables() {}

    /** Two-column table with header row `name | value` or `path | expected` → ordered map. */
    public static Map<String, String> asMap(DataTable table) {
        List<List<String>> rows = table.asLists();
        if (rows.size() < 2 || rows.getFirst().size() != 2)
            throw new IllegalArgumentException("Expected a two-column table with a header row, got " + rows);
        var out = new LinkedHashMap<String, String>();
        rows.stream().skip(1).forEach(r -> out.put(r.get(0), r.get(1) == null ? "" : r.get(1)));
        return out;
    }

    /** Vertical key/value table without header (used by `... exists with:`). */
    public static Map<String, String> asVerticalMap(DataTable table) {
        var out = new LinkedHashMap<String, String>();
        table.asLists().forEach(r -> out.put(r.get(0), r.get(1)));
        return out;
    }
}
```

## RequestSteps

```java
package com.company.automation.api.bdd.steps;

import com.company.automation.api.auth.TokenProvider;
import com.company.automation.api.bdd.context.ScenarioContext;
import com.company.automation.api.bdd.support.DataTables;
import com.company.automation.api.client.ApiResponse;
import com.company.automation.api.client.RequestSpec;
import com.company.automation.core.reporting.AllureAttachments;
import com.company.automation.core.utils.JsonUtils;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import java.util.HashMap;
import java.util.Map;

public class RequestSteps {

    private static final String NONE = "<none>";
    private final ScenarioContext ctx;

    public RequestSteps(ScenarioContext ctx) { this.ctx = ctx; }

    @Given("the API base URL is {string}")
    public void theApiBaseUrlIs(String url) {
        ctx.put("__baseUrl", ctx.resolve(url));            // informational; APIRequestContext already has baseURL
    }

    @Given("the request is authenticated as {string}")
    public void theRequestIsAuthenticatedAs(String role) { ctx.authRole(role); }

    @Given("the request is not authenticated")
    public void theRequestIsNotAuthenticated() { ctx.unauthenticated(); }

    @Given("the request uses an expired token")
    public void theRequestUsesAnExpiredToken() { ctx.expiredToken(); }

    @Given("the request has headers:")
    public void theRequestHasHeaders(DataTable table) {
        var spec = ctx.spec();
        for (var e : DataTables.asMap(table).entrySet()) {
            spec = NONE.equals(e.getValue()) ? spec.withoutHeader(e.getKey())
                                             : spec.withHeader(e.getKey(), ctx.resolve(e.getValue()));
        }
        ctx.spec(spec);
    }

    @Given("the request has query parameters:")
    public void theRequestHasQueryParameters(DataTable table) {
        var spec = ctx.spec();
        for (var e : DataTables.asMap(table).entrySet()) spec = spec.withQuery(e.getKey(), ctx.resolve(e.getValue()));
        ctx.spec(spec);
    }

    @Given("the request body is:")
    public void theRequestBodyIs(String docString) {
        ctx.spec(ctx.spec().withRawBody(ctx.resolve(docString)));   // raw: malformed-JSON scenarios must reach the server as-is
    }

    @When("I send a {word} request to {string}")
    public void iSendARequestTo(String method, String path) {
        send(method.toUpperCase(), ctx.resolve(path), ctx.spec());
    }

    @When("I send the same {word} request again with the same Idempotency-Key")
    public void iSendTheSameRequestAgain(String method) {
        send(method.toUpperCase(), ctx.lastPath(), ctx.spec());      // spec is intentionally not reset between sends
    }

    // ---- transport (the only place a request leaves the glue) ----
    private void send(String method, String path, RequestSpec spec) {
        var headers = new HashMap<>(spec.headers());
        ctx.authRole().ifPresent(role -> headers.put("Authorization", "Bearer " + TokenProvider.tokenFor(role)));
        if (ctx.useExpiredToken()) headers.put("Authorization", "Bearer " + TokenProvider.expiredToken());

        var options = RequestOptions.create().setMethod(method);
        headers.forEach(options::setHeader);
        spec.query().forEach(options::setQueryParam);
        if (spec.rawBody() != null) options.setData(spec.rawBody());

        AllureAttachments.text("Request " + method + " " + path,
            "headers=" + headers + System.lineSeparator() + "query=" + spec.query() + System.lineSeparator() + System.lineSeparator()
            + (spec.rawBody() == null ? "" : spec.rawBody()));

        long t0 = System.nanoTime();
        APIResponse resp = ctx.request().fetch(path, options);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        String raw = resp.text();
        AllureAttachments.json("Response %d (%d ms)".formatted(resp.status(), ms), raw);

        Map<String, Object> parsed = raw.isBlank() ? Map.of() : JsonUtils.tryParseMap(raw).orElse(Map.of());
        ctx.record(method, path, new ApiResponse<>(resp.status(), lower(resp.headers()), raw, parsed), ms);
    }

    private static Map<String, String> lower(Map<String, String> h) {
        var out = new HashMap<String, String>();
        h.forEach((k, v) -> out.put(k.toLowerCase(), v));
        return out;
    }
}
```

Notes:
- `RequestSpec` in the framework needs two small additions: `withRawBody(String)` / `rawBody()` and `withoutHeader(String)`. Add them there (framework change), not as glue hacks.
- `ScenarioContext.record` gains a `long elapsedMs` parameter and `lastPath()` accessor for the idempotency step and SLA step.
- `TokenProvider.tokenFor(role)` / `expiredToken()` come from the framework's `api/auth`; if missing, add them there.

## ResponseSteps

```java
package com.company.automation.api.bdd.steps;

import com.company.automation.api.bdd.context.ExpectedValueMatcher;
import com.company.automation.api.bdd.context.ScenarioContext;
import com.company.automation.api.bdd.context.SchemaRegistry;
import com.company.automation.api.bdd.support.DataTables;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import org.assertj.core.api.SoftAssertions;
import static org.assertj.core.api.Assertions.assertThat;

public class ResponseSteps {

    private final ScenarioContext ctx;

    public ResponseSteps(ScenarioContext ctx) { this.ctx = ctx; }

    @Then("the response status is {int}")
    public void theResponseStatusIs(int expected) {
        var r = ctx.lastResponse();
        assertThat(r.status())
            .as("%s → status. Body: %s", ctx.lastCall(), abbreviate(r.rawBody()))
            .isEqualTo(expected);
    }

    @Then("the response headers contain:")
    public void theResponseHeadersContain(DataTable table) {
        var r = ctx.lastResponse();
        var soft = new SoftAssertions();
        DataTables.asMap(table).forEach((name, expected) -> {
            var actual = r.headers().get(name.toLowerCase());
            var reason = ExpectedValueMatcher.check(actual == null ? ExpectedValueMatcher.ABSENT : actual, ctx.resolve(expected));
            soft.assertThat(reason).as("%s → header '%s'", ctx.lastCall(), name).isNull();
        });
        soft.assertAll();
    }

    @Then("the response body matches schema {string}")
    public void theResponseBodyMatchesSchema(String schemaPath) {
        var violations = SchemaRegistry.validate(schemaPath, ctx.lastResponse().rawBody());
        assertThat(violations)
            .as("%s → body does not match %s:%n%s", ctx.lastCall(), schemaPath, violations)
            .isEmpty();
    }

    @Then("the response body has:")
    public void theResponseBodyHas(DataTable table) {
        var raw = ctx.lastResponse().rawBody();
        var doc = JsonPath.parse(raw);
        var soft = new SoftAssertions();
        DataTables.asMap(table).forEach((path, expected) -> {
            Object actual;
            try { actual = doc.read(path); } catch (PathNotFoundException e) { actual = ExpectedValueMatcher.ABSENT; }
            var reason = ExpectedValueMatcher.check(actual, ctx.resolve(expected));
            soft.assertThat(reason).as("%s → %s", ctx.lastCall(), path).isNull();
        });
        soft.assertAll();
    }

    @Then("the response body does not contain {string}")
    public void theResponseBodyDoesNotContain(String text) {
        assertThat(ctx.lastResponse().rawBody())
            .as("%s → body must not contain '%s'", ctx.lastCall(), text)
            .doesNotContain(ctx.resolve(text));
    }

    @Then("the response time is under {int} ms")
    public void theResponseTimeIsUnder(int ms) {
        assertThat(ctx.lastElapsedMs()).as("%s → response time", ctx.lastCall()).isLessThan(ms);
    }

    private static String abbreviate(String s) { return s.length() > 300 ? s.substring(0, 300) + "…" : s; }
}
```

Soft assertions: a six-row body table reports all mismatches at once. `as(...)` always includes `lastCall()` so the failure text alone identifies the request.

## VariableSteps

```java
package com.company.automation.api.bdd.steps;

import com.company.automation.api.bdd.context.ScenarioContext;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.en.Then;

public class VariableSteps {

    private final ScenarioContext ctx;

    public VariableSteps(ScenarioContext ctx) { this.ctx = ctx; }

    @Then("store {string} as {string}")
    public void storeAs(String jsonPath, String name) {
        Object v = JsonPath.parse(ctx.lastResponse().rawBody()).read(jsonPath);
        if (v == null) throw new AssertionError(ctx.lastCall() + " → cannot store " + jsonPath + ": value is null");
        ctx.put(name, String.valueOf(v));
    }

    @Then("store header {string} as {string}")
    public void storeHeaderAs(String header, String name) {
        var v = ctx.lastResponse().headers().get(header.toLowerCase());
        if (v == null) throw new AssertionError(ctx.lastCall() + " → header '" + header + "' absent, cannot store as {" + name + "}");
        ctx.put(name, v);
    }
}
```

Both are `@Then` so they can follow assertions with `And`; Cucumber treats keywords as interchangeable at match time.

## PreconditionSteps

```java
package com.company.automation.api.bdd.steps;

import com.company.automation.api.bdd.context.ScenarioContext;
import com.company.automation.api.bdd.support.DataTables;
import com.company.automation.api.clients.UsersClient;
import com.company.automation.api.models.request.CreateUserRequest;
import com.company.automation.api.client.ApiClient;
import com.company.automation.api.auth.TokenProvider;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/** Creates data the scenario depends on, via the API layer, and registers its cleanup. */
public class PreconditionSteps {

    private static final Logger log = LoggerFactory.getLogger(PreconditionSteps.class);
    private final ScenarioContext ctx;

    public PreconditionSteps(ScenarioContext ctx) { this.ctx = ctx; }

    @Given("a user exists with:")
    public void aUserExistsWith(DataTable table) {
        Map<String, String> f = DataTables.asVerticalMap(table);
        var users = new UsersClient(adminContext());
        var resp = users.create(new CreateUserRequest(
            ctx.resolve(f.get("email")), ctx.resolve(f.getOrDefault("fullName", "Precondition User")), ctx.resolve(f.getOrDefault("role", "viewer"))));
        assertThat(resp.status()).as("precondition: create user %s", f).isEqualTo(201);
        var id = resp.body().id();
        ctx.record("POST", "/v1/users", toGeneric(resp), 0);       // so `store "$.id"` right after works
        ctx.onCleanup(() -> deleteQuietly(users, id));
    }

    @Given("the user {string} has status {string}")
    public void theUserHasStatus(String userId, String status) {
        var users = new UsersClient(adminContext());
        var resp = users.setStatus(ctx.resolve(userId), status);
        assertThat(resp.status()).as("precondition: set status %s", status).isEqualTo(200);
    }

    @Given("the created user {string} is deleted")
    public void theCreatedUserIsDeleted(String userId) {
        var id = ctx.resolve(userId);
        var resp = new UsersClient(adminContext()).delete(id);
        assertThat(resp.status()).as("cleanup: delete user %s", id).isIn(204, 404);
    }

    private com.microsoft.playwright.APIRequestContext adminContext() {
        return ctx.requestFor(Map.of("Authorization", "Bearer " + TokenProvider.tokenFor("admin")));
    }

    private static void deleteQuietly(UsersClient users, String id) {
        try { users.delete(id); } catch (RuntimeException e) { log.warn("cleanup failed for user {}: {}", id, e.toString()); }
    }

    private static com.company.automation.api.client.ApiResponse<Map<String, Object>> toGeneric(com.company.automation.api.client.ApiResponse<?> r) {
        return new com.company.automation.api.client.ApiResponse<>(r.status(), r.headers(), r.rawBody(),
            com.company.automation.core.utils.JsonUtils.tryParseMap(r.rawBody()).orElse(Map.of()));
    }
}
```

Rules embodied here:
- Preconditions always use the framework client with an **admin** context (`ctx.requestFor(headers)` caches one `APIRequestContext` per distinct header set within the scenario), independent of the scenario's own auth state — a 401 scenario must still be able to create its fixture.
- Every creation registers cleanup; the explicit `is deleted` step also exists because features call it, and it tolerates 404 (already cleaned).
- `deleteQuietly` logs and continues — cleanup must never mask the scenario's real result.
- Adding a resource = add `an order exists with:` and `the created order … is deleted` here, nothing more.
