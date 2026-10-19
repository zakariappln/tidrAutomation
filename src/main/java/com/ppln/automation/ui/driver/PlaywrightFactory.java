package com.ppln.automation.ui.driver;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.ppln.automation.core.config.ConfigManager;
import com.ppln.automation.core.config.FrameworkConfig;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Thread-scoped Playwright lifecycle for plain TestNG UI tests: one {@link Playwright} + {@link
 * Browser} per thread, one {@link BrowserContext} + {@link Page} per test method. Playwright
 * objects are not thread-safe, hence {@link ThreadLocal}; this is what makes {@code
 * parallel="methods"} safe.
 *
 * <p>Cucumber scenarios do not use this class; they own their objects in {@code UiScenarioContext}.
 */
public final class PlaywrightFactory {

    public static final Path TRACE_DIR = Paths.get("target", "traces");

    private static final ThreadLocal<Playwright> PLAYWRIGHT = new ThreadLocal<>();
    private static final ThreadLocal<Browser> BROWSER = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Page> PAGE = new ThreadLocal<>();

    private PlaywrightFactory() {}

    public static Page page() {
        return PAGE.get();
    }

    public static BrowserContext context() {
        return CONTEXT.get();
    }

    /**
     * Called from {@code @BeforeMethod}. {@code storageStatePath} may be null for anonymous
     * sessions.
     */
    public static Page startContext(Path storageStatePath) {
        FrameworkConfig cfg = ConfigManager.config();
        if (BROWSER.get() == null) {
            PLAYWRIGHT.set(Playwright.create());
            BROWSER.set(BrowserKind.from(cfg.browser()).launch(PLAYWRIGHT.get(), cfg));
        }
        BrowserContext ctx =
                BROWSER.get().newContext(ContextOptionsBuilder.from(cfg, storageStatePath));
        ctx.setDefaultTimeout(cfg.actionTimeoutMs());
        ctx.setDefaultNavigationTimeout(cfg.navigationTimeoutMs());
        startTracing(ctx, cfg);
        CONTEXT.set(ctx);
        PAGE.set(ctx.newPage());
        return PAGE.get();
    }

    /** Called from {@code @AfterMethod}. Returns the trace path if one was written, else null. */
    public static Path stopContext(String testName, boolean failed) {
        BrowserContext ctx = CONTEXT.get();
        Path trace = null;
        if (ctx != null) {
            trace = stopTracing(ctx, ConfigManager.config(), testName, failed);
            ctx.close(); // also flushes video
        }
        CONTEXT.remove();
        PAGE.remove();
        return trace;
    }

    /** Called from {@code @AfterClass}. */
    public static void shutdown() {
        Browser browser = BROWSER.get();
        if (browser != null) {
            browser.close();
            BROWSER.remove();
        }
        Playwright playwright = PLAYWRIGHT.get();
        if (playwright != null) {
            playwright.close();
            PLAYWRIGHT.remove();
        }
    }

    /** Shared with the BDD layer so both paths produce identical traces. */
    public static void startTracing(BrowserContext ctx, FrameworkConfig cfg) {
        if (cfg.traceMode().captureOnFailure()) {
            ctx.tracing()
                    .start(
                            new Tracing.StartOptions()
                                    .setScreenshots(true)
                                    .setSnapshots(true)
                                    .setSources(true));
        }
    }

    /** Stops tracing; keeps the zip when the test failed or the mode says always. */
    public static Path stopTracing(
            BrowserContext ctx, FrameworkConfig cfg, String name, boolean failed) {
        if (!cfg.traceMode().captureOnFailure()) {
            return null;
        }
        boolean keep = failed || cfg.traceMode().captureAlways();
        Path trace = keep ? TRACE_DIR.resolve(safeFileName(name) + ".zip") : null;
        ctx.tracing()
                .stop(
                        trace == null
                                ? new Tracing.StopOptions()
                                : new Tracing.StopOptions().setPath(trace));
        return trace;
    }

    public static String safeFileName(String name) {
        String cleaned = name.replaceAll("[^A-Za-z0-9._-]+", "_");
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }
}
