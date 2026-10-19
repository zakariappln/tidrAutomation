package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.options.AriaRole;
import com.ppln.automation.core.config.ConfigManager;
import com.ppln.automation.core.config.Credentials;
import com.ppln.automation.ui.auth.StorageStateManager;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.fixtures.AdminHttp;
import com.ppln.automation.ui.fixtures.FixtureUsers;
import com.ppln.automation.ui.pages.auth.LoginPage;
import com.ppln.automation.ui.pages.components.NavbarComponent;
import com.ppln.automation.ui.pages.dashboard.DashboardPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;

/**
 * Login/logout/rate-limit steps. Thin glue: parse arguments, call page objects, assert with
 * web-first assertions. Step classes are instantiated per scenario by PicoContainer, so instance
 * fields are scenario-scoped.
 */
public class AuthSteps {

    private final UiScenarioContext ctx;
    private LoginPage loginPage;
    private DashboardPage dashboardPage;

    public AuthSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Given("I open the login page")
    public void iOpenTheLoginPage() {
        loginPage = new LoginPage(ctx.page()).open();
    }

    /**
     * Used by every OTHER feature's Background. Reuses {@link StorageStateManager}'s saved session
     * (one real UI login per role, shared across scenarios and runs) instead of submitting the
     * login form again; if that session turns out to be dead, it is re-established once. This is
     * load-bearing, not just a speed-up: ThreatIDR's login POST is rate-limited to 3/minute by
     * source IP (confirmed live 2026-10-17 - even successful logins count), so a fresh UI login in
     * every scenario's Background would make the entire suite flaky under parallel execution. Only
     * {@code auth/login.feature} itself should drive the real login form repeatedly, and its
     * rate-limit-sensitive scenarios are tagged {@code @serial-auth} (see {@code SerialAuthHooks})
     * to avoid colliding with this cache-populating call.
     */
    @Given("I am logged in as {string}")
    public void iAmLoggedInAs(String role) {
        String resolved = ctx.resolve(role);
        Path state = StorageStateManager.forRole(resolved);
        FileTime version = StorageStateManager.versionOf(state);
        if (!ctx.hasPage()) {
            ctx.useStorageState(state); // a later call in the same scenario reuses the open page
        }
        ctx.put(CURRENT_ROLE, resolved);
        ctx.put(CURRENT_USERNAME, StorageStateManager.credentialsOf(resolved).username());
        dashboardPage = new DashboardPage(ctx.page()).open();
        // Session ended since it was saved (logout in another scenario, server restart, expiry)?
        // Then log in again here and re-save, so this and every later scenario keeps working.
        StorageStateManager.ensureAuthenticated(
                resolved, ctx.browserContext(), ctx.page(), version);
    }

    /** Scenario variable holding the role passed to "I am logged in as" (hidden from reports). */
    static final String CURRENT_ROLE = "__currentRole";

    /** Scenario variable holding the email of the account the browser is logged in as. */
    static final String CURRENT_USERNAME = "__currentUsername";

    @When("I log in as {string}")
    public void iLogInAs(String role) {
        Credentials credentials = ConfigManager.credentialsFor(ctx.resolve(role));
        dashboardPage = loginPage.loginAs(credentials.username(), credentials.password());
    }

    @When("I log in with username {string} and password {string}")
    public void iLogInWithUsernameAndPassword(String username, String password) {
        loginPage.loginExpectingError(ctx.resolve(username), ctx.resolve(password));
    }

    @Then("I should be on the dashboard")
    public void iShouldBeOnTheDashboard() {
        assertThat(ctx.page()).hasURL(Pattern.compile(".*/dashboard/?$"));
    }

    @Then("I should see the login error {string}")
    public void iShouldSeeTheLoginError(String message) {
        assertThat(loginPage.errorBanner()).containsText(ctx.resolve(message));
    }

    /**
     * ThreatIDR rate-limits the login POST to 3/minute (flask-limiter); the 4th attempt within the
     * window returns HTTP 429 and a full "Rate Limit Exceeded" page rather than an inline banner.
     */
    @When(
            "I attempt to log in {int} times in a row with username {string} and password"
                    + " {string}")
    public void iAttemptToLogInTimesInARow(int times, String username, String password) {
        for (int i = 0; i < times; i++) {
            loginPage.loginExpectingError(ctx.resolve(username), ctx.resolve(password));
        }
    }

    @Given("I have just been rate-limited after {int} failed login attempts")
    public void iHaveJustBeenRateLimitedAfterFailedLoginAttempts(int attempts) {
        String bait = ctx.resolve("{uniqueEmail}");
        for (int i = 0; i < attempts; i++) {
            loginPage.loginExpectingError(bait, "wrong-password");
        }
        loginPage.loginExpectingError(bait, "wrong-password"); // the (attempts + 1)th: triggers 429
        assertThat(
                        ctx.page()
                                .getByRole(
                                        AriaRole.HEADING,
                                        new com.microsoft.playwright.Page.GetByRoleOptions()
                                                .setName("Rate Limit Exceeded")))
                .isVisible();
    }

    @Then("I should see the rate-limit-exceeded page")
    public void iShouldSeeTheRateLimitExceededPage() {
        assertThat(
                        ctx.page()
                                .getByRole(
                                        AriaRole.HEADING,
                                        new com.microsoft.playwright.Page.GetByRoleOptions()
                                                .setName("Rate Limit Exceeded")))
                .isVisible();
    }

    /**
     * flask-limiter's default fixed window is 60s; this is a genuine time-based business behavior
     * under test (not UI timing), so an explicit Playwright wait is the correct tool here rather
     * than a retrying assertion with nothing yet to retry against.
     */
    @When("I wait for the rate-limit window to pass")
    public void iWaitForTheRateLimitWindowToPass() {
        ctx.page().waitForTimeout(61_000);
    }

    /** Both inputs carry {@code required}: the browser refuses to send the form. */
    @Then("the login form should not be submitted")
    public void theLoginFormShouldNotBeSubmitted() {
        assertThat(loginPage.emailInput()).isVisible();
        Assertions.assertThat(ctx.sentRequests())
                .as("requests sent by the page")
                .noneMatch(
                        r ->
                                r.startsWith("POST ")
                                        && r.matches("POST https?://[^/]+/[a-z]{2}/?(\\?.*)?"));
    }

    @Then("the {string} field should be flagged as required")
    public void theFieldShouldBeFlaggedAsRequired(String field) {
        Object valueMissing =
                loginPage.field(ctx.resolve(field)).evaluate("e => e.validity.valueMissing");
        Assertions.assertThat(valueMissing)
                .as("%s input reports validity.valueMissing", field)
                .isEqualTo(Boolean.TRUE);
    }

    /** Created as admin over HTTP, then deactivated through the edit form (is_active_edit = No). */
    @Given("an inactive user exists")
    public void anInactiveUserExists() {
        var admin = ctx.admin();
        FixtureUsers.Disposable user =
                FixtureUsers.createDisposable(admin, FixtureUsers.readReportsRoleId(admin));
        ctx.onCleanup(() -> ctx.admin().deleteUser(user.userId()));
        Map<String, String> form = AdminHttp.editForm(admin.user(user.userId()).orElseThrow());
        form.put("is_active_edit", "No");
        Assertions.assertThat(admin.updateUser(user.userId(), form).status())
                .as("deactivating the user")
                .isEqualTo(200);
        Assertions.assertThat(admin.user(user.userId()).orElseThrow().active()).isFalse();
        ctx.put(INACTIVE_EMAIL, user.credentials().username());
        ctx.put(INACTIVE_PASSWORD, user.credentials().password());
    }

    private static final String INACTIVE_EMAIL = "__inactiveEmail";
    private static final String INACTIVE_PASSWORD = "__inactivePassword";

    @When("I log in as that inactive user")
    public void iLogInAsThatInactiveUser() {
        loginPage.loginExpectingError(ctx.get(INACTIVE_EMAIL), ctx.get(INACTIVE_PASSWORD));
    }

    @When("I log out")
    public void iLogOut() {
        loginPage = new NavbarComponent(ctx.page()).logout();
    }
}
