package com.ppln.automation.ui.driver;

import com.microsoft.playwright.Browser;
import com.ppln.automation.core.config.ArtifactMode;
import com.ppln.automation.core.config.FrameworkConfig;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Builds {@link Browser.NewContextOptions} from config: viewport, locale, baseURL, storage state,
 * video.
 */
public final class ContextOptionsBuilder {

    public static final Path VIDEO_DIR = Paths.get("target", "videos");

    private ContextOptionsBuilder() {}

    /**
     * @param storageStatePath saved session to reuse, or {@code null} for an anonymous context
     */
    public static Browser.NewContextOptions from(FrameworkConfig cfg, Path storageStatePath) {
        var options =
                new Browser.NewContextOptions()
                        .setBaseURL(cfg.baseUrl())
                        .setViewportSize(cfg.viewportWidth(), cfg.viewportHeight())
                        .setLocale(cfg.locale())
                        .setAcceptDownloads(true);
        if (storageStatePath != null) {
            options.setStorageStatePath(storageStatePath);
        }
        if (cfg.videoMode() != ArtifactMode.OFF) {
            options.setRecordVideoDir(VIDEO_DIR);
        }
        return options;
    }
}
