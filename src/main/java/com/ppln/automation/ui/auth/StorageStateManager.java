package com.ppln.automation.ui.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.SameSiteAttribute;
import com.ppln.automation.core.config.ConfigManager;
import com.ppln.automation.core.config.Credentials;
import com.ppln.automation.core.config.FrameworkConfig;
import com.ppln.automation.core.utils.JsonUtils;
import com.ppln.automation.ui.driver.BrowserKind;
import com.ppln.automation.ui.driver.ContextOptionsBuilder;
import com.ppln.automation.ui.pages.auth.LoginPage;
import com.ppln.automation.ui.pages.dashboard.DashboardPage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs in once per role through the UI and persists the session as {@code
 * <auth.state.dir>/<env>/<role>.json} (default {@code .auth-state/}, git-ignored, outside {@code
 * target/} so {@code mvn clean} does not discard it). Tests reuse it via {@code
 * BaseUiTest#storageState()} / {@code UiScenarioContext#useStorageState}, so only the login feature
 * exercises the login form.
 *
 * <p>Resolution order on the first request for a role in a JVM:
 *
 * <ol>
 *   <li>a saved file younger than {@code auth.state.max.age.minutes} that the app still accepts
 *       (checked by opening the dashboard with those cookies: a few seconds, no login POST);
 *   <li>otherwise a real login through {@link LoginRateLimitGuard}, saved to the same file.
 * </ol>
 *
 * <p>If a page opened with the saved session shows the login form anyway (logout elsewhere, server
 * restart, expiry), {@link #ensureAuthenticated} logs in again in that context and re-saves the
 * file.
 *
 * <p>ThreatIDR sessions expire 15 minutes after the request that last signed the cookie, and a
 * saved file is frozen at save time. Every successful probe therefore writes the server's freshly
 * signed cookie back to the file, and a session in use is re-probed every {@code
 * auth.state.refresh.minutes} so a long run never outlives it.
 *
 * <p>Later requests in the same JVM return the cached path. {@link ConcurrentMap#computeIfAbsent}
 * makes parallel first requests for the same role share one resolution.
 */
public final class StorageStateManager {

    private static final Logger log = LoggerFactory.getLogger(StorageStateManager.class);
    private static final String DIR_KEY = "auth.state.dir";
    private static final String MAX_AGE_KEY = "auth.state.max.age.minutes";
    private static final String DEFAULT_DIR = ".auth-state";

    /** ThreatIDR sessions live 15 min from the last request (app.py); older files are dead. */
    private static final long DEFAULT_MAX_AGE_MINUTES = 14;

    private static final String REFRESH_KEY = "auth.state.refresh.minutes";

    /** Re-probe (and re-sign) a session in use this often so a long run never outlives it. */
    private static final long DEFAULT_REFRESH_MINUTES = 10;

    /** The app's login rate limit is 3/minute; 61s clears a full window with a safety margin. */
    private static final long RATE_LIMIT_WINDOW_MS = 61_000;

    private static final ConcurrentMap<String, Path> STATES = new ConcurrentHashMap<>();

    /** When each role's saved session was last confirmed and re-signed by the server (ms). */
    private static final ConcurrentMap<String, Long> CONFIRMED_AT = new ConcurrentHashMap<>();

    private static final Object RECOVERY = new Object();

    /** Sessions registered with explicit credentials (fixture users), keyed by session name. */
    private static final ConcurrentMap<String, Credentials> EXPLICIT = new ConcurrentHashMap<>();

    private StorageStateManager() {}

    public static Path forRole(String role) {
        String key = normalise(role);
        Path file = STATES.computeIfAbsent(key, StorageStateManager::resolve);
        refreshIfStale(key, file);
        return file;
    }

    /**
     * Like {@link #forRole} for an account that is not in config: a test fixture user created at
     * runtime. The session is saved as {@code <name>.json} next to the role sessions and gets the
     * same reuse, probe, 15-minute re-signing and recovery. Calling it again with a different
     * password (the account was recreated) drops the saved session first.
     *
     * @param name session name, e.g. {@code fixture-report-read}; must not clash with a role
     */
    public static Path forCredentials(String name, Credentials credentials) {
        String key = normalise(name);
        Credentials previous = EXPLICIT.put(key, credentials);
        if (previous != null && !previous.password().equals(credentials.password())) {
            invalidate(key);
        }
        return forRole(key);
    }

    /** Credentials behind a session name: an explicit registration, else config for the role. */
    public static Credentials credentialsOf(String name) {
        String key = normalise(name);
        Credentials explicit = EXPLICIT.get(key);
        return explicit != null ? explicit : ConfigManager.credentialsFor(key);
    }

    /** Directory holding the saved sessions of the selected environment. */
    public static Path stateDir() {
        return Paths.get(ConfigManager.optional(DIR_KEY).orElse(DEFAULT_DIR))
                .resolve(ConfigManager.config().env().key());
    }

    /**
     * The app expires a session 15 min after the request that last signed its cookie, and the saved
     * file is frozen at save time. So, every {@code auth.state.refresh.minutes}, the session in use
     * is probed again: the server answers with a freshly signed cookie, which is written back to
     * the file. A rejected probe (server restart, revoked session) logs in again.
     */
    private static void refreshIfStale(String key, Path file) {
        long now = System.currentTimeMillis();
        if (now - CONFIRMED_AT.getOrDefault(key, 0L) < refreshMinutes() * 60_000) {
            return;
        }
        synchronized (RECOVERY) {
            if (now - CONFIRMED_AT.getOrDefault(key, 0L) < refreshMinutes() * 60_000) {
                return;
            }
            if (sessionStillAccepted(file)) {
                log.info("Refreshed the saved session for role '{}' ({})", key, file);
            } else {
                log.info("Saved session for role '{}' no longer accepted; logging in again", key);
                login(key, file);
            }
            CONFIRMED_AT.put(key, System.currentTimeMillis());
        }
    }

    /**
     * Drops the cached session for a role, in memory and on disk, so the next {@link #forRole} logs
     * in again (e.g. after a password-change or "all sessions revoked" test).
     */
    public static void invalidate(String role) {
        String key = normalise(role);
        STATES.remove(key);
        Path file = stateFile(key);
        try {
            if (Files.deleteIfExists(file)) {
                log.info("Deleted saved session for role '{}' ({})", key, file);
            }
        } catch (IOException e) {
            log.warn("Could not delete saved session {}: {}", file, e.toString());
        }
    }

    /** Where the session for a role is (or would be) saved for the selected environment. */
    public static Path stateFile(String role) {
        return stateDir().resolve(normalise(role) + ".json");
    }

    private static Path resolve(String role) {
        Path file = stateFile(role);
        if (isReusable(role, file)) {
            log.info("Reusing saved session for role '{}' from {}", role, file);
            CONFIRMED_AT.put(role, System.currentTimeMillis());
            return file;
        }
        return login(role, file);
    }

    private static boolean isReusable(String role, Path file) {
        if (!Files.isRegularFile(file)) {
            log.info("No saved session for role '{}' at {}; logging in", role, file);
            return false;
        }
        long ageMinutes;
        try {
            ageMinutes =
                    Duration.between(Files.getLastModifiedTime(file).toInstant(), Instant.now())
                            .toMinutes();
        } catch (IOException e) {
            log.warn("Cannot read {}: {}; logging in", file, e.toString());
            return false;
        }
        long maxAge = maxAgeMinutes();
        if (ageMinutes > maxAge) {
            log.info(
                    "Saved session for role '{}' is {} min old (limit {} min); logging in again",
                    role,
                    ageMinutes,
                    maxAge);
            return false;
        }
        if (!sessionStillAccepted(file)) {
            log.info("Saved session for role '{}' was rejected by the app; logging in again", role);
            return false;
        }
        return true;
    }

    /**
     * Requests the dashboard over plain HTTP with the saved cookies (no browser: about a second).
     * {@code true} when the app serves the dashboard, {@code false} when it redirects to the login
     * form or anything goes wrong. On success the response's freshly signed cookie is written back
     * to the file, which restarts the app's 15-minute session lifetime for every later reader.
     */
    private static boolean sessionStillAccepted(Path file) {
        FrameworkConfig cfg = ConfigManager.config();
        String dashboardPath = DashboardPage.PATH;
        try (Playwright playwright = Playwright.create()) {
            APIRequestContext request =
                    playwright
                            .request()
                            .newContext(
                                    new APIRequest.NewContextOptions()
                                            .setBaseURL(cfg.baseUrl())
                                            .setStorageStatePath(file)
                                            .setTimeout(cfg.navigationTimeoutMs()));
            try {
                APIResponse response = request.get(dashboardPath);
                boolean accepted = response.ok() && response.url().contains(dashboardPath);
                if (!accepted) {
                    log.info(
                            "Saved session check: GET {} -> {} at {}",
                            dashboardPath,
                            response.status(),
                            response.url());
                    return false;
                }
                request.storageState(new APIRequestContext.StorageStateOptions().setPath(file));
                return true;
            } catch (PlaywrightException e) {
                log.warn("Could not verify saved session {}: {}", file, e.getMessage());
                return false;
            } finally {
                request.dispose();
            }
        }
    }

    /** Modification time of a saved session, or {@code null} when it does not exist yet. */
    public static FileTime versionOf(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.getLastModifiedTime(file) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Call after opening an authenticated page with the saved session. If the app rendered the
     * login form instead (the session was ended by a logout elsewhere, a server restart or expiry),
     * re-authenticates <em>this</em> context and re-saves the file so every later scenario and run
     * reuses the new session. When another thread already refreshed the file since {@code
     * seenVersion}, its cookies are injected instead of logging in again, so parallel scenarios
     * that all hit one dead session cost one login, not four.
     *
     * @param seenVersion the file's {@link #versionOf} when this context was created
     * @return {@code true} when a recovery happened
     */
    public static boolean ensureAuthenticated(
            String role, BrowserContext context, Page page, FileTime seenVersion) {
        Locator shell = new DashboardPage(page).loadedMarker();
        Locator loginForm = new LoginPage(page).emailInput();
        shell.or(loginForm).first().waitFor();
        if (shell.isVisible()) {
            return false;
        }
        String key = normalise(role);
        Path file = stateFile(key);
        log.warn(
                "Saved session for role '{}' was not accepted (login form shown); recovering", key);
        synchronized (RECOVERY) {
            FileTime now = versionOf(file);
            if (now != null && (seenVersion == null || now.compareTo(seenVersion) > 0)) {
                log.info("Session for role '{}' was already refreshed; reusing it", key);
                context.clearCookies();
                context.addCookies(cookiesOf(file));
                page.navigate(DashboardPage.PATH);
                shell.waitFor();
                return true;
            }
            LoginRateLimitGuard.acquire();
            try {
                submitLogin(page, key, credentialsOf(key));
                Files.createDirectories(file.getParent());
                context.storageState(new BrowserContext.StorageStateOptions().setPath(file));
                STATES.put(key, file);
                CONFIRMED_AT.put(key, System.currentTimeMillis());
                log.info("Re-authenticated role '{}' and saved the session to {}", key, file);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot persist storage state for role " + key, e);
            } finally {
                LoginRateLimitGuard.release();
            }
        }
        return true;
    }

    /** The cookies of a saved storage-state file, for injection into a live context. */
    static List<Cookie> cookiesOf(Path file) {
        JsonNode root;
        try {
            root = JsonUtils.readTree(Files.readString(file));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read saved session " + file, e);
        }
        var cookies = new ArrayList<Cookie>();
        for (JsonNode c : root.path("cookies")) {
            Cookie cookie =
                    new Cookie(c.path("name").asText(), c.path("value").asText())
                            .setDomain(c.path("domain").asText())
                            .setPath(c.path("path").asText("/"))
                            .setHttpOnly(c.path("httpOnly").asBoolean(false))
                            .setSecure(c.path("secure").asBoolean(false));
            if (c.hasNonNull("expires")) {
                cookie.setExpires(c.path("expires").asDouble());
            }
            String sameSite = c.path("sameSite").asText("");
            if (!sameSite.isBlank()) {
                cookie.setSameSite(SameSiteAttribute.valueOf(sameSite.toUpperCase(Locale.ROOT)));
            }
            cookies.add(cookie);
        }
        return cookies;
    }

    /**
     * Goes through {@link LoginRateLimitGuard} like every other real login (see its javadoc): this
     * is the lazy, cache-populating call that previously raced {@code auth/login.feature}'s own
     * rate-limit testing and lost, confirmed live 2026-10-17.
     */
    private static Path login(String role, Path target) {
        LoginRateLimitGuard.acquire();
        try {
            return loginUnguarded(role, target);
        } finally {
            LoginRateLimitGuard.release();
        }
    }

    private static Path loginUnguarded(String role, Path target) {
        FrameworkConfig cfg = ConfigManager.config();
        Credentials credentials = credentialsOf(role);
        try (Playwright playwright = Playwright.create()) {
            Browser browser = BrowserKind.from(cfg.browser()).launch(playwright, cfg);
            BrowserContext context = browser.newContext(sessionOptions(cfg));
            context.setDefaultTimeout(cfg.actionTimeoutMs());
            context.setDefaultNavigationTimeout(cfg.navigationTimeoutMs());
            Page page = context.newPage();
            submitLogin(page, role, credentials); // session is only worth saving once accepted
            Files.createDirectories(target.getParent());
            context.storageState(new BrowserContext.StorageStateOptions().setPath(target));
            context.close();
            browser.close();
            log.info("Logged in as role '{}' and saved the session to {}", role, target);
            CONFIRMED_AT.put(role, System.currentTimeMillis());
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot persist storage state for role " + role, e);
        }
    }

    /**
     * Submits the login form and waits for the authenticated shell. If the app answers with its
     * "Rate Limit Exceeded" page (logins made outside this JVM exhausted the 3/minute window),
     * waits the window out once and retries, rather than making every run pay that wait up front.
     */
    private static void submitLogin(Page page, String role, Credentials credentials) {
        for (int attempt = 1; ; attempt++) {
            var dashboard =
                    new LoginPage(page)
                            .<LoginPage>open()
                            .loginAs(credentials.username(), credentials.password());
            Locator shell = dashboard.loadedMarker();
            Locator rateLimited =
                    page.getByRole(
                            AriaRole.HEADING,
                            new Page.GetByRoleOptions().setName("Rate Limit Exceeded"));
            shell.or(rateLimited).first().waitFor();
            if (shell.isVisible()) {
                return;
            }
            if (attempt >= 2) {
                throw new IllegalStateException(
                        "Login for role '%s' is still rate-limited after waiting out the window"
                                .formatted(role));
            }
            log.warn(
                    "Login for role '{}' hit the rate limit (logins outside this run); waiting {} ms"
                            + " for the window to clear, then retrying once",
                    role,
                    RATE_LIMIT_WINDOW_MS);
            page.waitForTimeout(RATE_LIMIT_WINDOW_MS);
        }
    }

    /**
     * Plain context for login / validation: no video, no tracing (see {@link
     * ContextOptionsBuilder}).
     */
    private static Browser.NewContextOptions sessionOptions(FrameworkConfig cfg) {
        return new Browser.NewContextOptions()
                .setBaseURL(cfg.baseUrl())
                .setViewportSize(cfg.viewportWidth(), cfg.viewportHeight())
                .setLocale(cfg.locale());
    }

    private static long refreshMinutes() {
        return ConfigManager.optional(REFRESH_KEY)
                .map(Long::parseLong)
                .orElse(DEFAULT_REFRESH_MINUTES);
    }

    private static long maxAgeMinutes() {
        return ConfigManager.optional(MAX_AGE_KEY)
                .map(Long::parseLong)
                .orElse(DEFAULT_MAX_AGE_MINUTES);
    }

    private static String normalise(String role) {
        return role.trim().toLowerCase(Locale.ROOT);
    }
}
