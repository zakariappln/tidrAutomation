package com.ppln.automation.ui.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppln.automation.core.config.ConfigManager;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Requests made with NO session at all - not even the scenario's browser context, which may carry a
 * cached admin login. Used to prove (or disprove) that an endpoint is protected. Redirects are
 * deliberately not followed, so a "redirect to login" shows up as a 3xx rather than as the login
 * page's 200.
 */
public class AnonymousHttpSteps {

    private static final String STATUS = "__anonStatus";
    private static final String BODY = "__anonBody";
    private static final String LOCATION = "__anonLocation";

    /** The login page: {@code /<lang>/}, optionally with a query ({@code ?next=...}). */
    private static final String LOGIN_LOCATION = "(https?://[^/]+)?/[a-z]{2}/(\\?.*)?";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final UiScenarioContext ctx;

    public AnonymousHttpSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    @When("I call {string} without an active session")
    public void iCallWithoutAnActiveSession(String path) throws IOException, InterruptedException {
        HttpClient client =
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(ConfigManager.config().baseUrl() + ctx.resolve(path)))
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        ctx.put(STATUS, String.valueOf(response.statusCode()));
        ctx.put(BODY, response.body());
        ctx.put(LOCATION, response.headers().firstValue("Location").orElse(""));
    }

    /** Unauthenticated requests are bounced to the login page with a 3xx, never served. */
    @Then("the anonymous response should be a redirect to the login page")
    public void theAnonymousResponseShouldBeARedirectToTheLoginPage() {
        assertThat(Integer.parseInt(ctx.get(STATUS))).as("anonymous status").isBetween(300, 399);
        assertThat(ctx.get(LOCATION)).as("Location header").matches(LOGIN_LOCATION);
    }

    @Then("the anonymous response status should be {int}")
    public void theAnonymousResponseStatusShouldBe(int status) {
        assertThat(Integer.parseInt(ctx.get(STATUS))).isEqualTo(status);
    }

    /** The whole body (surrounding whitespace ignored) matches the regular expression. */
    @Then("the anonymous response body should match {string}")
    public void theAnonymousResponseBodyShouldMatch(String regex) {
        assertThat(ctx.get(BODY).strip())
                .matches(Pattern.compile(ctx.resolve(regex), Pattern.DOTALL));
    }

    @Then("the anonymous response body should not contain {string}")
    public void theAnonymousResponseBodyShouldNotContain(String text) {
        assertThat(ctx.get(BODY)).doesNotContainIgnoringCase(ctx.resolve(text));
    }

    @Then("the anonymous response body should be a non-empty JSON list")
    public void theAnonymousResponseBodyShouldBeANonEmptyJsonList() throws IOException {
        JsonNode node = JSON.readTree(ctx.get(BODY));
        assertThat(node.isArray()).as("response body is a JSON array").isTrue();
        assertThat(node.size()).as("JSON array size").isPositive();
    }
}
