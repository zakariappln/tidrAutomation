package com.ppln.automation.ui.driver;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.ppln.automation.core.config.FrameworkConfig;
import java.util.Locale;

/**
 * Supported browsers ({@code -Dbrowser=chromium|firefox|webkit|chrome|msedge}). Named {@code
 * BrowserKind} to avoid clashing with Playwright's own {@link BrowserType}.
 */
public enum BrowserKind {
    CHROMIUM,
    FIREFOX,
    WEBKIT,
    CHROME,
    MSEDGE;

    public static BrowserKind from(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Unknown browser '%s'. Use chromium, firefox, webkit, chrome or msedge"
                            .formatted(value),
                    e);
        }
    }

    /** Launches this browser with the headless/slow-mo switches from config. */
    public Browser launch(Playwright playwright, FrameworkConfig cfg) {
        var options =
                new BrowserType.LaunchOptions()
                        .setHeadless(cfg.headless())
                        .setSlowMo(cfg.slowMoMs());
        return switch (this) {
            case CHROMIUM -> playwright.chromium().launch(options);
            case FIREFOX -> playwright.firefox().launch(options);
            case WEBKIT -> playwright.webkit().launch(options);
            case CHROME -> playwright.chromium().launch(options.setChannel("chrome"));
            case MSEDGE -> playwright.chromium().launch(options.setChannel("msedge"));
        };
    }
}
