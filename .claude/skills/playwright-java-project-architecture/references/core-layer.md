# Core Layer

Everything here is shared by UI and API. Keep it small; if a class is only used by one layer, it does not belong here.

## ConfigManager + FrameworkConfig

```java
package com.company.automation.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

public final class ConfigManager {

    private static final FrameworkConfig CONFIG = load();

    private ConfigManager() {}

    public static FrameworkConfig config() { return CONFIG; }

    private static FrameworkConfig load() {
        var props = new Properties();
        loadInto(props, "config/default.properties");
        var env = resolve("env", props).orElse("qa");
        loadInto(props, "config/%s.properties".formatted(env));
        return new FrameworkConfig(
            Environment.valueOf(env.toUpperCase(Locale.ROOT)),
            get("base.url", props),
            get("api.base.url", props),
            get("browser", props),
            Boolean.parseBoolean(get("headless", props)),
            Integer.parseInt(get("slow.mo.ms", props)),
            Integer.parseInt(get("viewport.width", props)),
            Integer.parseInt(get("viewport.height", props)),
            get("locale", props),
            Integer.parseInt(get("timeout.action.ms", props)),
            Integer.parseInt(get("timeout.navigation.ms", props)),
            Integer.parseInt(get("timeout.expect.ms", props)),
            ArtifactMode.from(get("trace.mode", props)),
            ArtifactMode.from(get("video.mode", props)),
            ArtifactMode.from(get("screenshot.mode", props)),
            Integer.parseInt(get("retry.count", props)),
            Integer.parseInt(get("api.timeout.ms", props)),
            get("auth.username", props),
            get("auth.password", props)
        );
    }

    /** Precedence: -D system property > ENV_VAR > properties file. */
    private static String get(String key, Properties props) {
        return resolve(key, props)
            .orElseThrow(() -> new IllegalStateException("Missing config key: " + key));
    }

    private static java.util.Optional<String> resolve(String key, Properties props) {
        var sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) return java.util.Optional.of(sys);
        var envVar = System.getenv(key.toUpperCase(Locale.ROOT).replace('.', '_'));
        if (envVar != null && !envVar.isBlank()) return java.util.Optional.of(envVar);
        return java.util.Optional.ofNullable(props.getProperty(key)).filter(v -> !v.isBlank());
    }

    private static void loadInto(Properties props, String classpathFile) {
        try (InputStream in = ConfigManager.class.getClassLoader().getResourceAsStream(classpathFile)) {
            if (in == null) return;            // env-specific file is optional
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + classpathFile, e);
        }
    }
}
```

```java
public record FrameworkConfig(
    Environment env,
    String baseUrl, String apiBaseUrl,
    String browser, boolean headless, int slowMoMs,
    int viewportWidth, int viewportHeight, String locale,
    int actionTimeoutMs, int navigationTimeoutMs, int expectTimeoutMs,
    ArtifactMode traceMode, ArtifactMode videoMode, ArtifactMode screenshotMode,
    int retryCount, int apiTimeoutMs,
    String authUsername, String authPassword) {}

public enum Environment { QA, STAGING, PROD }

public enum ArtifactMode {
    OFF, ON, ON_FAILURE, RETAIN_ON_FAILURE;
    public static ArtifactMode from(String v) {
        return valueOf(v.trim().toUpperCase().replace('-', '_'));
    }
    public boolean captureAlways()      { return this == ON || this == RETAIN_ON_FAILURE; }
    public boolean captureOnFailure()   { return this != OFF; }
}
```

Why: one immutable record, resolved once, thread-safe by construction. Tests never touch `System.getProperty` directly.

## PlaywrightFactory (UI layer, shown here because listeners depend on it)

Lives in `ui/driver`. One `Playwright` + `Browser` per thread, one `BrowserContext` + `Page` per test method. Playwright objects are **not** thread-safe, hence `ThreadLocal`.

```java
package com.company.automation.ui.driver;

import com.company.automation.core.config.ConfigManager;
import com.company.automation.core.config.FrameworkConfig;
import com.microsoft.playwright.*;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class PlaywrightFactory {

    private static final ThreadLocal<Playwright> PLAYWRIGHT = new ThreadLocal<>();
    private static final ThreadLocal<Browser> BROWSER = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Page> PAGE = new ThreadLocal<>();

    private PlaywrightFactory() {}

    public static Page page()            { return PAGE.get(); }
    public static BrowserContext context(){ return CONTEXT.get(); }

    /** Called from @BeforeMethod. storageStatePath may be null for anonymous sessions. */
    public static Page startContext(Path storageStatePath) {
        FrameworkConfig cfg = ConfigManager.config();
        if (BROWSER.get() == null) {
            PLAYWRIGHT.set(Playwright.create());
            BROWSER.set(launch(PLAYWRIGHT.get(), cfg));
        }
        var options = ContextOptionsBuilder.from(cfg, storageStatePath);
        var ctx = BROWSER.get().newContext(options);
        ctx.setDefaultTimeout(cfg.actionTimeoutMs());
        ctx.setDefaultNavigationTimeout(cfg.navigationTimeoutMs());
        if (cfg.traceMode().captureOnFailure()) {
            ctx.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true).setSnapshots(true).setSources(true));
        }
        CONTEXT.set(ctx);
        PAGE.set(ctx.newPage());
        return PAGE.get();
    }

    /** Called from @AfterMethod. Returns trace path if one was written, else null. */
    public static Path stopContext(String testName, boolean failed) {
        FrameworkConfig cfg = ConfigManager.config();
        Path trace = null;
        var ctx = CONTEXT.get();
        if (ctx != null) {
            if (cfg.traceMode().captureOnFailure()) {
                boolean keep = failed || cfg.traceMode().captureAlways();
                trace = keep ? Paths.get("target/traces", testName + ".zip") : null;
                ctx.tracing().stop(trace == null ? new Tracing.StopOptions()
                                                 : new Tracing.StopOptions().setPath(trace));
            }
            ctx.close();                // also flushes video
        }
        CONTEXT.remove(); PAGE.remove();
        return trace;
    }

    /** Called from @AfterClass / @AfterSuite via listener. */
    public static void shutdown() {
        if (BROWSER.get() != null) { BROWSER.get().close(); BROWSER.remove(); }
        if (PLAYWRIGHT.get() != null) { PLAYWRIGHT.get().close(); PLAYWRIGHT.remove(); }
    }

    private static Browser launch(Playwright pw, FrameworkConfig cfg) {
        var opts = new BrowserType.LaunchOptions()
            .setHeadless(cfg.headless()).setSlowMo(cfg.slowMoMs());
        return switch (cfg.browser().toLowerCase()) {
            case "firefox"  -> pw.firefox().launch(opts);
            case "webkit"   -> pw.webkit().launch(opts);
            case "chrome"   -> pw.chromium().launch(opts.setChannel("chrome"));
            case "msedge"   -> pw.chromium().launch(opts.setChannel("msedge"));
            default         -> pw.chromium().launch(opts);
        };
    }
}
```

`ContextOptionsBuilder.from(cfg, storageState)` sets viewport, locale, `setBaseURL(cfg.baseUrl())`, `setStorageStatePath` when non-null, `setRecordVideoDir(Paths.get("target/videos"))` when `videoMode != OFF`, and `setAcceptDownloads(true)`.

## TestListener

```java
package com.company.automation.core.listeners;

import com.company.automation.core.reporting.AllureAttachments;
import com.company.automation.ui.driver.PlaywrightFactory;
import org.testng.ITestListener;
import org.testng.ITestResult;

public class TestListener implements ITestListener {

    @Override
    public void onTestFailure(ITestResult result) {
        var page = PlaywrightFactory.page();
        if (page != null && !page.isClosed()) {          // UI test
            AllureAttachments.screenshot(page, "Failure screenshot");
            AllureAttachments.text("Page URL", page.url());
            AllureAttachments.consoleLogs();               // collected by BaseUiTest via page.onConsoleMessage
        }
        // API attachments are added at request time by ApiClient, nothing to do here.
    }
}
```

Trace/video attachment happens in `BaseUiTest#tearDown` because the context must be closed first (see `ui-layer.md`).

## RetryAnalyzer + AnnotationTransformer

```java
public class RetryAnalyzer implements IRetryAnalyzer {
    private int attempt = 0;
    @Override public boolean retry(ITestResult result) {
        return attempt++ < ConfigManager.config().retryCount();
    }
}

public class AnnotationTransformer implements IAnnotationTransformer {
    @Override public void transform(ITestAnnotation a, Class c, Constructor ctor, Method m) {
        if (a.getRetryAnalyzerClass() == null) a.setRetryAnalyzer(RetryAnalyzer.class);
    }
}
```

Retries mask flakiness; keep `retry.count` at 1 and make the Allure `categories.json` surface "Flaky (passed on retry)" so they are visible, not hidden.

## SuiteListener

Writes `target/allure-results/environment.properties` (env, browser, base URLs, Java version, Playwright version) at `onStart`. See `allure-conventions.md`.

## Logging

SLF4J + Logback. `logback-test.xml` with pattern `%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n`. Never `System.out`. Log at INFO for navigation and API calls, DEBUG for locator actions.
