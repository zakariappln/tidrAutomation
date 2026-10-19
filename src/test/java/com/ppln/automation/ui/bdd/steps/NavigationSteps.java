package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.TimeoutError;
import com.ppln.automation.core.utils.JsRegex;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.pages.auth.LoginPage;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;

/**
 * Generic, cross-feature navigation steps: direct URL access (with or without a session) and the
 * "landed on the login page" assertion reused by every authorization/redirect scenario. Module-
 * specific navigation ("I am on the X page") lives in that module's own step class.
 */
public class NavigationSteps {

    private final UiScenarioContext ctx;

    public NavigationSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    /**
     * The scenario's {@link UiScenarioContext} starts a fresh, anonymous browser context by default
     * (no storage state), so simply navigating is sufficient to exercise an anonymous visitor - no
     * explicit "log out first" is needed.
     */
    @When("I navigate directly to {string} without logging in")
    public void iNavigateDirectlyToWithoutLoggingIn(String path) {
        ctx.page().navigate(ctx.resolve(path));
    }

    /** Uses the scenario's current session (admin, a limited fixture user, ...). */
    @When("I navigate directly to {string}")
    public void iNavigateDirectlyTo(String path) {
        ctx.page().navigate(ctx.resolve(path));
    }

    @When("I navigate back in the browser history")
    public void iNavigateBackInTheBrowserHistory() {
        ctx.page().goBack();
    }

    @Then("I should be on the login page")
    public void iShouldBeOnTheLoginPage() {
        assertThat(new LoginPage(ctx.page()).emailInput()).isVisible();
    }

    /** The page finished loading somewhere other than the login form. */
    @Then("I should not be on the login page")
    public void iShouldNotBeOnTheLoginPage() {
        Integer status = ctx.lastDocumentStatus();
        Assertions.assertThat(status).as("status of %s", ctx.page().url()).isEqualTo(200);
        assertThat(new LoginPage(ctx.page()).emailInput()).hasCount(0);
    }

    /** The path of the current URL starts with {@code prefix}, e.g. {@code "/ja/"}. */
    @Then("the URL should start with {string}")
    public void theUrlShouldStartWith(String prefix) {
        assertThat(ctx.page())
                .hasURL(
                        Pattern.compile(
                                "^https?://[^/]+" + JsRegex.quote(ctx.resolve(prefix)) + ".*"));
    }

    /**
     * The current URL's query has {@code name} = {@code value} once decoded (pages write their
     * state with {@code encodeURIComponent}, so "7 days" appears as {@code 7%20days}). Waits for
     * it, because pages update the URL with {@code history.replaceState} after the action.
     */
    @Then("the URL should contain the query parameter {string} with value {string}")
    public void theUrlShouldContainTheQueryParameterWithValue(String name, String value) {
        String key = ctx.resolve(name);
        String expected = ctx.resolve(value);
        try {
            ctx.page()
                    .waitForCondition(() -> expected.equals(queryParameter(ctx.page().url(), key)));
        } catch (TimeoutError e) {
            Assertions.fail(
                    "Query parameter %s is %s in %s, expected %s",
                    key, queryParameter(ctx.page().url(), key), ctx.page().url(), expected);
        }
    }

    private static String queryParameter(String url, String name) {
        String query = URI.create(url).getRawQuery();
        if (query == null) {
            return null;
        }
        return Arrays.stream(query.split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(kv -> name.equals(URLDecoder.decode(kv[0], StandardCharsets.UTF_8)))
                .map(kv -> kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "")
                .findFirst()
                .orElse(null);
    }
}
