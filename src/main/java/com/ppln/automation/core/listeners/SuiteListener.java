package com.ppln.automation.core.listeners;

import com.microsoft.playwright.Playwright;
import com.ppln.automation.core.config.ConfigManager;
import com.ppln.automation.core.config.FrameworkConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ISuite;
import org.testng.ISuiteListener;

/**
 * Writes {@code environment.properties}, {@code categories.json} and (in CI) {@code executor.json}
 * into the Allure results directory so every report carries its context.
 */
public class SuiteListener implements ISuiteListener {

    private static final Logger log = LoggerFactory.getLogger(SuiteListener.class);

    @Override
    public void onStart(ISuite suite) {
        FrameworkConfig cfg = loadConfigOrExplain();
        Path results = resultsDirectory();
        try {
            Files.createDirectories(results);
            Files.writeString(
                    results.resolve("environment.properties"),
                    environment(cfg, suite),
                    StandardCharsets.UTF_8);
            copyResource("categories.json", results.resolve("categories.json"));
            executorJson().ifPresent(json -> write(results.resolve("executor.json"), json));
        } catch (IOException e) {
            log.warn("Could not write Allure environment files: {}", e.toString());
        }
        log.info(
                "Suite '{}' | env={} | ui={} | api={} | browser={} headless={}",
                suite.getName(),
                cfg.env().key(),
                cfg.baseUrl(),
                cfg.apiBaseUrl(),
                cfg.browser(),
                cfg.headless());
    }

    @Override
    public void onFinish(ISuite suite) {
        log.info("Suite '{}' finished", suite.getName());
    }

    /**
     * Configuration is resolved in a static initialiser; a failure there surfaces as an opaque
     * {@link ExceptionInInitializerError} in Surefire's dump file. Log the root cause so the
     * console says what to fix (missing env file, PROD guard, missing key), then stop the suite.
     */
    private static FrameworkConfig loadConfigOrExplain() {
        try {
            return ConfigManager.config();
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            log.error("CONFIGURATION ERROR - the suite cannot start: {}", root.getMessage());
            throw new IllegalStateException("Configuration error: " + root.getMessage(), root);
        }
    }

    static Path resultsDirectory() {
        return Paths.get(System.getProperty("allure.results.directory", "target/allure-results"));
    }

    private static String environment(FrameworkConfig cfg, ISuite suite) {
        return """
                Suite=%s
                Environment=%s
                Browser=%s (%s)
                UI.BaseUrl=%s
                API.BaseUrl=%s
                Java=%s
                Playwright=%s
                Executor=%s
                """
                .formatted(
                        suite.getName(),
                        cfg.env().key(),
                        cfg.browser(),
                        cfg.headless() ? "headless" : "headed",
                        cfg.baseUrl(),
                        cfg.apiBaseUrl(),
                        Runtime.version().feature(),
                        Optional.ofNullable(
                                        Playwright.class.getPackage().getImplementationVersion())
                                .orElse("see pom.xml"),
                        Optional.ofNullable(System.getenv("GITHUB_RUN_ID"))
                                .map(id -> "GitHub Actions #" + id)
                                .orElseGet(() -> System.getProperty("user.name")));
    }

    private static Optional<String> executorJson() {
        String runId = System.getenv("GITHUB_RUN_ID");
        if (runId == null) {
            return Optional.empty();
        }
        String server =
                Optional.ofNullable(System.getenv("GITHUB_SERVER_URL"))
                        .orElse("https://github.com");
        String repo = Optional.ofNullable(System.getenv("GITHUB_REPOSITORY")).orElse("");
        String runNumber = Optional.ofNullable(System.getenv("GITHUB_RUN_NUMBER")).orElse(runId);
        return Optional.of(
                """
                {
                  "name": "GitHub Actions",
                  "type": "github",
                  "buildOrder": %s,
                  "buildName": "%s #%s",
                  "buildUrl": "%s/%s/actions/runs/%s",
                  "reportName": "Allure Report"
                }
                """
                        .formatted(runNumber, repo, runNumber, server, repo, runId));
    }

    private static void copyResource(String classpathFile, Path target) throws IOException {
        try (InputStream in =
                SuiteListener.class.getClassLoader().getResourceAsStream(classpathFile)) {
            if (in != null) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void write(Path target, String content) {
        try {
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Could not write {}: {}", target, e.toString());
        }
    }
}
