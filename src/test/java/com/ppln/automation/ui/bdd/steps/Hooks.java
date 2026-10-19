package com.ppln.automation.ui.bdd.steps;

import com.ppln.automation.core.reporting.AllureAttachments;
import com.ppln.automation.ui.bdd.context.SoftChecks;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.qameta.allure.Allure;
import java.util.Deque;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;

/** UI scenario lifecycle: tracking links, failure artifacts, LIFO cleanup, browser disposal. */
public class Hooks {

    private static final Logger log = LoggerFactory.getLogger(Hooks.class);
    private final UiScenarioContext ctx;
    private final SoftChecks soft;

    public Hooks(UiScenarioContext ctx, SoftChecks soft) {
        this.ctx = ctx;
        this.soft = soft;
    }

    /**
     * {@code soft.checks=true}: verification steps record failures and the scenario continues; see
     * {@link SoftChecks}.
     */
    @Before(order = 2)
    public void enableSoftChecks(Scenario scenario) {
        soft.enable(com.ppln.automation.core.config.ConfigManager.config().softChecks());
        log.info(
                "Soft checks {}",
                soft.enabled()
                        ? "ENABLED (failures are reported at the end)"
                        : "disabled (first failure stops)");
    }

    /** Runs before every other @After: turns recorded soft failures into the scenario's failure. */
    @After(order = 110)
    public void failOnSoftFailures(Scenario scenario) {
        List<String> failures = soft.failures();
        if (!failures.isEmpty()) {
            String summary =
                    failures.size()
                            + " soft assertion(s) failed:"
                            + System.lineSeparator()
                            + "- "
                            + String.join(System.lineSeparator() + "- ", failures);
            AllureAttachments.text("Soft assertion failures", summary);
            throw new AssertionError(summary);
        }
    }

    @Before(order = 0)
    public void start(Scenario scenario) {
        log.info(
                "SCENARIO {} [{}]",
                scenario.getName(),
                String.join(" ", scenario.getSourceTagNames()));
        scenario.getSourceTagNames().stream()
                .filter(t -> t.startsWith("@TC-") || t.startsWith("@JIRA-"))
                .forEach(t -> Allure.tms(t.substring(1), null));
    }

    /** Skip scenarios tagged @wip unless explicitly requested with @run-wip. */
    @Before(value = "@wip and not @run-wip", order = 1)
    public void skipWip(Scenario scenario) {
        throw new SkipException("Scenario tagged @wip");
    }

    @After(order = 100) // runs first among @After hooks
    public void attachOnFailure(Scenario scenario) {
        if (!scenario.isFailed() || !ctx.hasPage()) {
            return;
        }
        AllureAttachments.screenshot(ctx.page(), "Failure screenshot");
        AllureAttachments.text("Page URL", ctx.page().url());
        if (!ctx.consoleMessages().isEmpty()) {
            AllureAttachments.text(
                    "Browser console", String.join(System.lineSeparator(), ctx.consoleMessages()));
        }
        String variables = ctx.variablesForReport();
        if (!variables.isBlank()) {
            AllureAttachments.text("Stored variables", variables);
        }
    }

    @After(order = 50)
    public void runCleanups(Scenario scenario) {
        Deque<Runnable> cleanups = ctx.cleanups();
        int failed = 0;
        while (!cleanups.isEmpty()) {
            try {
                cleanups.pop().run();
            } catch (RuntimeException e) {
                failed++;
                log.warn("cleanup failed in '{}': {}", scenario.getName(), e.toString());
            }
        }
        if (failed > 0) {
            AllureAttachments.text(
                    "Cleanup warnings", failed + " cleanup action(s) failed; see log");
        }
    }

    @After(order = 0) // runs last
    public void dispose(Scenario scenario) {
        try {
            var artifacts = ctx.finish(scenario.getName(), scenario.isFailed());
            if (artifacts.trace() != null) {
                AllureAttachments.trace(artifacts.trace());
            }
            if (artifacts.video() != null) {
                AllureAttachments.video(artifacts.video());
            }
        } finally {
            ctx.close();
            log.info("{} {}", scenario.isFailed() ? "FAILED" : "PASSED", scenario.getName());
        }
    }
}
