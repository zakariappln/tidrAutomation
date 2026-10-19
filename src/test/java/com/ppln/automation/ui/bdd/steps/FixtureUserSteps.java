package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.ppln.automation.ui.auth.LoginRateLimitGuard;
import com.ppln.automation.ui.auth.StorageStateManager;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.fixtures.FixtureUsers;
import com.ppln.automation.ui.pages.auth.LoginPage;
import com.ppln.automation.ui.pages.dashboard.DashboardPage;
import io.cucumber.java.en.Given;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

/**
 * Log in as a non-admin account (see features/ui/README.md, "Limited users").
 *
 * <ul>
 *   <li>Limited users are persistent fixtures ({@link FixtureUsers}): created once as admin, reused
 *       across runs, session cached by {@link StorageStateManager}.
 *   <li>These steps <em>replace</em> the scenario's session: a feature Background usually logged in
 *       as admin already, so the browser context is swapped, not extended.
 *   <li>A disposable user is created per scenario, logged in once through the login form and
 *       deleted in cleanup; for scenarios that change the account itself (password change).
 * </ul>
 */
public class FixtureUserSteps {

    /** Password of the account the scenario is logged in as, when the scenario created it. */
    static final String CURRENT_PASSWORD = "__currentPassword";

    /** Id of the account the scenario is logged in as, when it is a fixture or disposable user. */
    static final String CURRENT_USER_ID = "__currentUserId";

    private final UiScenarioContext ctx;

    public FixtureUserSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Given("I am logged in as a user with only the {string} permission")
    public void iAmLoggedInAsAUserWithOnlyThePermission(String permissionLabel) {
        switchTo(FixtureUsers.withPermissions(List.of(ctx.resolve(permissionLabel))));
    }

    @Given("I am logged in as a user holding every grantable permission")
    public void iAmLoggedInAsAUserHoldingEveryGrantablePermission() {
        switchTo(FixtureUsers.withEveryGrantablePermission());
    }

    private void switchTo(FixtureUsers.Fixture fixture) {
        Path state = fixture.storageState();
        FileTime version = StorageStateManager.versionOf(state);
        ctx.replaceSession(state);
        ctx.put(AuthSteps.CURRENT_ROLE, fixture.sessionName());
        ctx.put(AuthSteps.CURRENT_USERNAME, fixture.credentials().username());
        ctx.put(CURRENT_USER_ID, Integer.toString(fixture.userId()));
        new DashboardPage(ctx.page()).open();
        StorageStateManager.ensureAuthenticated(
                fixture.sessionName(), ctx.browserContext(), ctx.page(), version);
    }

    /**
     * Costs one real login: tag the scenario {@code @serial-auth} so the slot is reserved up front.
     * Outside such a scenario the step takes the guard itself.
     */
    @Given("I am logged in as a disposable user")
    public void iAmLoggedInAsADisposableUser() {
        var admin = ctx.admin();
        FixtureUsers.Disposable user =
                FixtureUsers.createDisposable(admin, FixtureUsers.readReportsRoleId(admin));
        ctx.onCleanup(() -> ctx.admin().deleteUser(user.userId()));
        ctx.put(AuthSteps.CURRENT_USERNAME, user.credentials().username());
        ctx.put(CURRENT_PASSWORD, user.credentials().password());
        ctx.put(CURRENT_USER_ID, Integer.toString(user.userId()));
        ctx.replaceSession(null);
        boolean reserved = LoginRateLimitGuard.heldByCurrentThread();
        if (!reserved) {
            LoginRateLimitGuard.acquire();
        }
        try {
            DashboardPage dashboard =
                    new LoginPage(ctx.page())
                            .<LoginPage>open()
                            .loginAs(user.credentials().username(), user.credentials().password());
            assertThat(dashboard.loadedMarker()).isVisible();
        } finally {
            if (!reserved) {
                LoginRateLimitGuard.release();
            }
        }
    }
}
