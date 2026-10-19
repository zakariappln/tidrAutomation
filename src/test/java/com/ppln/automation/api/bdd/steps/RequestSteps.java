package com.ppln.automation.api.bdd.steps;

import com.ppln.automation.api.auth.AuthHeaders;
import com.ppln.automation.api.auth.TokenProvider;
import com.ppln.automation.api.bdd.context.MultipartFixtures;
import com.ppln.automation.api.bdd.context.ScenarioContext;
import com.ppln.automation.api.client.RawApiClient;
import com.ppln.automation.api.client.RequestSpec;
import com.ppln.automation.core.bdd.DataTables;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import java.util.Locale;

/**
 * Given: headers, query, body, auth state. When: send. Transport goes through {@link RawApiClient}.
 */
public class RequestSteps {

    private static final String NONE = "<none>";
    private final ScenarioContext ctx;

    public RequestSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Given("the API base URL is {string}")
    public void theApiBaseUrlIs(String url) {
        ctx.put(
                "__baseUrl",
                ctx.resolve(url)); // informational; the APIRequestContext already carries baseURL
    }

    @Given("the request is authenticated as {string}")
    public void theRequestIsAuthenticatedAs(String role) {
        ctx.authRole(ctx.resolve(role));
    }

    @Given("the request is not authenticated")
    public void theRequestIsNotAuthenticated() {
        ctx.unauthenticated();
    }

    @Given("the request uses an expired token")
    public void theRequestUsesAnExpiredToken() {
        ctx.expiredToken();
    }

    @Given("the request has headers:")
    public void theRequestHasHeaders(DataTable table) {
        RequestSpec spec = ctx.spec();
        for (var e : DataTables.asMap(table).entrySet()) {
            spec =
                    NONE.equals(e.getValue())
                            ? spec.withoutHeader(e.getKey())
                            : spec.withHeader(e.getKey(), ctx.resolve(e.getValue()));
        }
        ctx.spec(spec);
    }

    @Given("the request has query parameters:")
    public void theRequestHasQueryParameters(DataTable table) {
        RequestSpec spec = ctx.spec();
        for (var e : DataTables.asMap(table).entrySet()) {
            spec = spec.withQuery(e.getKey(), ctx.resolve(e.getValue()));
        }
        ctx.spec(spec);
    }

    @Given("the request body is:")
    public void theRequestBodyIs(String docString) {
        ctx.spec(
                ctx.spec()
                        .withRawBody(
                                ctx.resolve(
                                        docString))); // raw: malformed-JSON scenarios must reach
        // the server as-is
    }

    /**
     * Fields come from {@code testdata/multipart/<Controller>.<Action>.json}, chosen by the path
     * sent next.
     */
    @Given("I prepare a multipart request with the form fields")
    public void iPrepareAMultipartRequestWithTheFormFields() {
        ctx.requestMultipart();
    }

    @When("I send a {word} request to {string}")
    public void iSendARequestTo(String method, String path) {
        send(method, ctx.resolve(path));
    }

    @When("I send the same {word} request again with the same Idempotency-Key")
    public void iSendTheSameRequestAgain(String method) {
        send(method, ctx.lastPath()); // spec is intentionally not reset between sends
    }

    private void send(String method, String path) {
        RequestSpec spec = ctx.spec();
        if (ctx.multipartRequested()) {
            spec = spec.withMultipart(MultipartFixtures.load(path, ctx.resolver()));
        }
        if (ctx.useExpiredToken()) {
            spec = spec.withHeaders(AuthHeaders.bearer(TokenProvider.expiredToken()));
        } else if (ctx.authRole().isPresent()) {
            spec =
                    spec.withHeaders(
                            AuthHeaders.bearer(TokenProvider.tokenFor(ctx.authRole().get())));
        }
        String verb = method.toUpperCase(Locale.ROOT);
        var response = new RawApiClient(ctx.request()).exchange(verb, path, spec);
        ctx.record(verb, path, response);
    }
}
