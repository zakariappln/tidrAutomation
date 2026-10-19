package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.ppln.automation.core.utils.JsRegex;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.bdd.context.UiScenarioContext.HttpResult;
import com.ppln.automation.ui.pages.common.AccessDeniedPage;
import com.ppln.automation.ui.pages.components.MessagesComponent;
import com.ppln.automation.ui.pages.components.SidebarComponent;
import io.cucumber.java.en.Then;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;

/**
 * Cross-feature outcome checks from the shared vocabulary (features/ui/README.md): access denial,
 * server errors, sidebar entries, messages and static text. One method per phrase; every feature
 * reuses these instead of adding its own variant.
 */
public class PageAssertionSteps {

    private final UiScenarioContext ctx;

    public PageAssertionSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Then("I should see an access-denied page")
    public void iShouldSeeAnAccessDeniedPage() {
        assertThat(new AccessDeniedPage(ctx.page()).heading()).isVisible();
    }

    /** The page rendered normally: no 401 page and still inside the authenticated shell. */
    @Then("I should not see an access-denied page")
    public void iShouldNotSeeAnAccessDeniedPage() {
        assertThat(new AccessDeniedPage(ctx.page()).heading()).hasCount(0);
        assertThat(new SidebarComponent(ctx.page()).root()).isVisible();
    }

    /** The last full page load (navigation) answered below 500. */
    @Then("the page should load without a server error")
    public void thePageShouldLoadWithoutAServerError() {
        Integer status = ctx.lastDocumentStatus();
        Assertions.assertThat(status).as("status of the last page load").isNotNull();
        Assertions.assertThat(status).as("HTTP status of %s", ctx.page().url()).isLessThan(500);
    }

    /** No request in this scenario so far (the page's own AJAX or a step's) answered 5xx. */
    @Then("the request should not cause a server error")
    public void theRequestShouldNotCauseAServerError() {
        ctx.awaitPendingCalls();
        Assertions.assertThat(ctx.serverErrors()).as("5xx responses in this scenario").isEmpty();
    }

    @Then("the sidebar should show the menu label {string}")
    public void theSidebarShouldShowTheMenuLabel(String label) {
        assertThat(new SidebarComponent(ctx.page()).menuLink(ctx.resolve(label))).hasCount(1);
    }

    @Then("the sidebar should not show the menu label {string}")
    public void theSidebarShouldNotShowTheMenuLabel(String label) {
        SidebarComponent sidebar = new SidebarComponent(ctx.page());
        assertThat(sidebar.root()).isVisible(); // the shell rendered, so absence is meaningful
        assertThat(sidebar.menuLink(ctx.resolve(label))).hasCount(0);
    }

    /** A toast or dialog (SweetAlert2), or a flashed alert of a server-rendered page. */
    @Then("I should see the message {string}")
    public void iShouldSeeTheMessage(String text) {
        String resolved = ctx.resolve(text);
        MessagesComponent messages = new MessagesComponent(ctx.page());
        assertThat(messages.withText(resolved).or(messages.flashedAlert(resolved)).first())
                .isVisible();
    }

    /** Static page text (headings, labels), not data values. */
    @Then("I should see the text {string}")
    public void iShouldSeeTheText(String text) {
        assertThat(
                        ctx.page()
                                .getByText(
                                        ctx.resolve(text),
                                        new Page.GetByTextOptions().setExact(true))
                                .first())
                .isVisible();
    }

    @Then("I should see the {string} button")
    public void iShouldSeeTheButton(String label) {
        assertThat(
                        ctx.page()
                                .getByRole(
                                        AriaRole.BUTTON,
                                        new Page.GetByRoleOptions()
                                                .setName(ctx.resolve(label))
                                                .setExact(true)))
                .isVisible();
    }

    /** The error of the form the scenario submitted last (registered by that form's step). */
    @Then("I should see a required-field error for {string}")
    public void iShouldSeeARequiredFieldErrorFor(String field) {
        assertThat(ctx.fieldError(ctx.resolve(field))).isVisible();
    }

    @Then("I should see a validation error for {string}")
    public void iShouldSeeAValidationErrorFor(String field) {
        assertThat(ctx.fieldError(ctx.resolve(field))).isVisible();
        assertThat(ctx.fieldError(ctx.resolve(field))).not().isEmpty();
    }

    /**
     * The request a step sent itself ({@code ctx.lastHttp()}) was refused. ThreatIDR refuses in
     * several shapes (docs/threatidr-source-facts.md section 0, "Response conventions"): HTTP
     * 401/403; a bounce to the login page (step requests follow redirects, so it arrives as the
     * login form, {@code #loginForm}); the 401.html denial page served with HTTP 200; or the JSON
     * {@code [{"errors":"Unauthorized"},401]} served with HTTP 200. Anything else - in particular a
     * 200 carrying the requested data, or a redirect somewhere other than the login page - is not a
     * denial.
     */
    @Then("the request should be denied")
    public void theRequestShouldBeDenied() {
        HttpResult result = ctx.lastHttp();
        String body = result.body() == null ? "" : result.body();
        boolean statusDenied = result.status() == 401 || result.status() == 403;
        boolean bouncedToLogin = body.contains("id=\"loginForm\""); // templates/home/login.html
        boolean deniedJson = UNAUTHORIZED_JSON.matcher(body).find();
        boolean deniedPage = body.contains("401 Unauthorized!"); // templates/401.html heading
        Assertions.assertThat(statusDenied || bouncedToLogin || deniedJson || deniedPage)
                .as(
                        "%s %s answered HTTP %d with %s",
                        result.method(),
                        result.url(),
                        result.status(),
                        body.length() > 300 ? body.substring(0, 300) + "..." : body)
                .isTrue();
    }

    private static final Pattern UNAUTHORIZED_JSON =
            Pattern.compile("\\{\\s*\"errors\"\\s*:\\s*\"Unauthorized\"\\s*}\\s*,\\s*40[13]");

    /** {@code <title>} of the current page ends with this text (surrounding blanks ignored). */
    @Then("the browser title should end with {string}")
    public void theBrowserTitleShouldEndWith(String suffix) {
        assertThat(ctx.page())
                .hasTitle(Pattern.compile(".*" + JsRegex.quote(ctx.resolve(suffix)) + "\\s*$"));
    }
}
