package com.ppln.automation.ui.bdd.context;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.ppln.automation.core.bdd.PlaceholderResolver;
import com.ppln.automation.core.config.ConfigManager;
import com.ppln.automation.core.config.FrameworkConfig;
import com.ppln.automation.ui.driver.BrowserKind;
import com.ppln.automation.ui.driver.ContextOptionsBuilder;
import com.ppln.automation.ui.driver.PlaywrightFactory;
import com.ppln.automation.ui.fixtures.AdminHttp;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Per-scenario UI state, created by PicoContainer and injected into step classes and hooks. Owns
 * the scenario's Playwright objects (created lazily on first {@link #page()}), stored variables,
 * console buffer and LIFO cleanup stack. No static state, no ThreadLocal.
 */
public final class UiScenarioContext implements AutoCloseable {

    /** Artifacts that survive context close; either path may be null. */
    public record Artifacts(Path trace, Path video) {}

    /** Status and body of a request a step sent itself (outside the page's own traffic). */
    public record HttpResult(String method, String url, int status, String body) {}

    private final FrameworkConfig cfg = ConfigManager.config();
    private final Map<String, String> variables = new HashMap<>();
    private final Deque<Runnable> cleanups = new ArrayDeque<>();
    private final List<String> console = new ArrayList<>();
    private final PlaceholderResolver resolver = new PlaceholderResolver(variables);

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    private Path storageState;
    private final List<BrowserContext> extraContexts = new ArrayList<>();
    private final List<HttpResult> serverErrors = new ArrayList<>();
    private final List<String> sentRequests = new ArrayList<>();
    private Integer lastDocumentStatus;
    private HttpResult lastHttp;
    private AdminHttp admin;
    private Function<String, Locator> fieldErrors;
    private final List<Runnable> pendingCalls = new ArrayList<>();

    public PlaceholderResolver resolver() {
        return resolver;
    }

    public String resolve(String text) {
        return resolver.resolve(text);
    }

    // ---- variables ----
    public void put(String key, String value) {
        variables.put(key, value);
    }

    public boolean has(String key) {
        return variables.containsKey(key);
    }

    public String get(String key) {
        String v = variables.get(key);
        if (v == null) {
            throw new IllegalStateException(
                    "No stored variable {" + key + "}. Stored: " + variables.keySet());
        }
        return v;
    }

    public String variablesForReport() {
        return variables.entrySet().stream()
                .filter(e -> !e.getKey().startsWith("__"))
                .map(e -> e.getKey() + " = " + e.getValue())
                .collect(Collectors.joining(System.lineSeparator()));
    }

    // ---- browser ----
    /**
     * Must be called before the first {@link #page()}; e.g. {@code
     * StorageStateManager.forRole("admin")}.
     */
    public void useStorageState(Path path) {
        if (page != null) {
            throw new IllegalStateException(
                    "Storage state must be chosen before the page is opened");
        }
        storageState = path;
    }

    public Page page() {
        if (page == null) {
            start();
        }
        return page;
    }

    public BrowserContext browserContext() {
        page();
        return context;
    }

    public boolean hasPage() {
        return page != null && !page.isClosed();
    }

    public List<String> consoleMessages() {
        return List.copyOf(console);
    }

    /** The scenario's Playwright instance (created on first use), e.g. for HTTP fixtures. */
    public Playwright playwright() {
        if (playwright == null) {
            PlaywrightAssertions.setDefaultAssertionTimeout(cfg.expectTimeoutMs());
            playwright = Playwright.create();
        }
        return playwright;
    }

    /**
     * Admin-session HTTP helper for preconditions and cleanups, independent of whichever account
     * the scenario's browser is logged in as. Created on first use, disposed with the scenario.
     */
    public AdminHttp admin() {
        if (admin == null) {
            admin = new AdminHttp(playwright());
        }
        return admin;
    }

    private Browser browser() {
        if (browser == null) {
            browser = BrowserKind.from(cfg.browser()).launch(playwright(), cfg);
        }
        return browser;
    }

    private void start() {
        context = browser().newContext(ContextOptionsBuilder.from(cfg, storageState));
        context.setDefaultTimeout(cfg.actionTimeoutMs());
        context.setDefaultNavigationTimeout(cfg.navigationTimeoutMs());
        PlaywrightFactory.startTracing(context, cfg);
        page = context.newPage();
        page.onConsoleMessage(m -> console.add("[%s] %s".formatted(m.type(), m.text())));
        page.onRequest(
                r -> {
                    if (!"GET".equals(r.method())) {
                        synchronized (sentRequests) {
                            sentRequests.add(r.method() + " " + r.url());
                        }
                    }
                });
        page.onResponse(
                r -> {
                    if (r.request().isNavigationRequest() && r.frame() == page.mainFrame()) {
                        lastDocumentStatus = r.status();
                    }
                    if (r.status() >= 500) {
                        recordServerError(
                                new HttpResult(r.request().method(), r.url(), r.status(), null));
                    }
                });
    }

    /**
     * Replaces the scenario's session: a feature Background usually logs in as admin, and a later
     * step switches to another account. If the page is already open its context is closed (the
     * trace so far is discarded) and a new context is started with {@code newStorageState};
     * otherwise this only selects the storage state. {@code null} gives an anonymous session.
     */
    public void replaceSession(Path newStorageState) {
        storageState = newStorageState;
        if (context == null) {
            return;
        }
        PlaywrightFactory.stopTracing(context, cfg, "replaced-session", false);
        context.close();
        context = null;
        page = null;
        lastDocumentStatus = null;
        start();
    }

    /**
     * A second, independent browser session (own cookies) in the scenario's browser, e.g. "the
     * other user is logged in elsewhere". Closed with the scenario.
     */
    public Page openIsolatedSession(Path storageStatePath) {
        BrowserContext extra =
                browser().newContext(ContextOptionsBuilder.from(cfg, storageStatePath));
        extra.setDefaultTimeout(cfg.actionTimeoutMs());
        extra.setDefaultNavigationTimeout(cfg.navigationTimeoutMs());
        extraContexts.add(extra);
        return extra.newPage();
    }

    // ---- responses observed in this scenario ----

    /** HTTP status of the last main-frame document the page loaded, or null before any. */
    public Integer lastDocumentStatus() {
        return lastDocumentStatus;
    }

    /** Every non-GET request the scenario's page sent, as {@code "METHOD url"}. */
    public List<String> sentRequests() {
        synchronized (sentRequests) {
            return List.copyOf(sentRequests);
        }
    }

    /** Every 5xx seen so far: the page's own traffic plus requests steps sent themselves. */
    public List<HttpResult> serverErrors() {
        synchronized (serverErrors) {
            return List.copyOf(serverErrors);
        }
    }

    private void recordServerError(HttpResult result) {
        synchronized (serverErrors) {
            serverErrors.add(result);
        }
    }

    /** Records a request a step sent itself; 5xx answers also count as server errors. */
    public void recordHttp(HttpResult result) {
        lastHttp = result;
        if (result.status() >= 500) {
            recordServerError(result);
        }
    }

    /**
     * Registers a wait for a call the page is expected to send after a later, generic step (for
     * example {@code When I click "Generate"}, which does not know what it triggers). Outcome steps
     * run {@link #awaitPendingCalls()} first, so they never judge before the answer is in.
     */
    public void expectPendingCall(Runnable awaitCall) {
        pendingCalls.add(awaitCall);
    }

    /** Runs and clears every wait registered with {@link #expectPendingCall}. */
    public void awaitPendingCalls() {
        List<Runnable> waits = List.copyOf(pendingCalls);
        pendingCalls.clear();
        waits.forEach(Runnable::run);
    }

    public HttpResult lastHttp() {
        if (lastHttp == null) {
            throw new IllegalStateException("No request has been sent by a step in this scenario");
        }
        return lastHttp;
    }

    // ---- field errors of the form a step just submitted ----

    /**
     * Registers how to find the error shown for a field of the form the step just submitted, so the
     * shared "I should see a required-field error for ..." / "a validation error for ..." phrases
     * work on every form.
     */
    public void useFieldErrors(Function<String, Locator> errorForField) {
        fieldErrors = errorForField;
    }

    public Locator fieldError(String field) {
        if (fieldErrors == null) {
            throw new IllegalStateException("No form was submitted in this scenario yet");
        }
        return fieldErrors.apply(field);
    }

    // ---- record originals / restore ----

    /**
     * Reads a value before a step changes it and registers its restore as a cleanup, e.g. {@code
     * ctx.restoreAfterwards(settings::timezone, settings::setTimezone)}. Combine with
     * {@code @serial-settings} for global state so no other scenario sees the change.
     *
     * @return the original value
     */
    public <T> T restoreAfterwards(Supplier<T> readOriginal, Consumer<T> restore) {
        T original = readOriginal.get();
        onCleanup(() -> restore.accept(original));
        return original;
    }

    // ---- cleanup (LIFO) ----
    public void onCleanup(Runnable action) {
        cleanups.push(action);
    }

    public Deque<Runnable> cleanups() {
        return cleanups;
    }

    /**
     * Stops tracing, closes the context and returns the artifact paths. Video path is captured
     * before close because the file is only complete afterwards.
     */
    public Artifacts finish(String scenarioName, boolean failed) {
        Path trace = null;
        Path video = null;
        if (context != null) {
            video = failed && page != null && page.video() != null ? page.video().path() : null;
            trace = PlaywrightFactory.stopTracing(context, cfg, scenarioName, failed);
            context.close();
            context = null;
            page = null;
        }
        return new Artifacts(trace, video);
    }

    @Override
    public void close() {
        extraContexts.forEach(BrowserContext::close);
        extraContexts.clear();
        if (admin != null) {
            admin.close();
            admin = null;
        }
        if (context != null) {
            context.close();
            context = null;
            page = null;
        }
        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }
}
