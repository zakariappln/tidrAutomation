package com.ppln.automation.ui.bdd.steps;

import com.ppln.automation.ui.auth.LoginRateLimitGuard;
import com.ppln.automation.ui.auth.StorageStateManager;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;

/**
 * Serialises scenarios tagged {@code @serial-auth}: anything that submits ThreatIDR's login form
 * for real (as opposed to reusing {@code StorageStateManager}'s saved session), via the shared
 * {@link LoginRateLimitGuard} - see its javadoc for why this must be shared with {@code
 * StorageStateManager} rather than kept local to this class.
 *
 * <p>Tag only the scenarios that submit the form, never a whole feature: every tagged scenario
 * queues behind the guard. A plain {@code @serial-auth} scenario reserves one login POST, so three
 * of them run back to back inside one rate-limit window. A scenario that deliberately exhausts the
 * limit (4 attempts) adds {@code @login-window} and gets the whole window to itself.
 *
 * <p>The {@code order} values place the acquire before the scenario's own {@code @Before} work and
 * the release after every other {@code @After} (including {@code Hooks#dispose}, which runs at
 * order 0) - see the ordering note on {@link Hooks}: Cucumber runs {@code @After} hooks in
 * <em>descending</em> order, so a more negative number runs later.
 *
 * <p>{@link LoginRateLimitGuard#acquire(int)} uses {@code Thread.sleep} despite the project's "no
 * Thread.sleep" rule: that rule targets arbitrary waits for UI timing, which Playwright's
 * auto-retrying assertions make unnecessary; this wait throttles a real external rate limiter
 * between login attempts, where no element or condition exists yet to retry against (no page may
 * even be open). If this needs revisiting, ask rather than deleting it to "fix" the rule violation
 * - removing it reintroduces the cascade failure described on {@link LoginRateLimitGuard}.
 */
public class SerialAuthHooks {

    static final String WHOLE_WINDOW_TAG = "@login-window";

    @Before(value = "@serial-auth", order = -100)
    public void acquire(Scenario scenario) {
        // Resolve the admin session BEFORE taking the guard: steps of guarded scenarios use it for
        // preconditions (ctx.admin()), and resolving it while holding the guard deadlocks against
        // another thread that is already resolving it and waiting for the guard to log in.
        StorageStateManager.forRole("admin");
        boolean wholeWindow = scenario.getSourceTagNames().contains(WHOLE_WINDOW_TAG);
        LoginRateLimitGuard.acquire(wholeWindow ? LoginRateLimitGuard.LIMIT : 1);
    }

    @After(value = "@serial-auth", order = -100)
    public void release() {
        LoginRateLimitGuard.release();
    }
}
