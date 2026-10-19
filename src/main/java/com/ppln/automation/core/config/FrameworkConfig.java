package com.ppln.automation.core.config;

/**
 * Immutable view of the resolved configuration for the selected {@code env}. Built once by {@link
 * ConfigManager}; tests never read {@code System.getProperty} directly.
 */
public record FrameworkConfig(
        Environment env,
        String baseUrl,
        String apiBaseUrl,
        String browser,
        boolean headless,
        int slowMoMs,
        int viewportWidth,
        int viewportHeight,
        String locale,
        int actionTimeoutMs,
        int navigationTimeoutMs,
        int expectTimeoutMs,
        ArtifactMode traceMode,
        ArtifactMode videoMode,
        ArtifactMode screenshotMode,
        int retryCount,
        int apiTimeoutMs,
        String authBaseUrl,
        String authLoginPath,
        String authTokenJsonPointer,
        boolean softChecks) {

    /**
     * {@code soft.checks}: when true, verification steps record failures and the scenario continues
     * (failing at the end); when false the first failing check stops the scenario.
     */
    public boolean softChecks() {
        return softChecks;
    }

    /** Login host; falls back to the API base URL when {@code auth.base.url} is blank. */
    public String effectiveAuthBaseUrl() {
        return authBaseUrl == null || authBaseUrl.isBlank() ? apiBaseUrl : authBaseUrl;
    }
}
