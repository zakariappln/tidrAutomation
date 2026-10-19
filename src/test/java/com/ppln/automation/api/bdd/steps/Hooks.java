package com.ppln.automation.api.bdd.steps;

import com.ppln.automation.api.bdd.context.ScenarioContext;
import com.ppln.automation.core.reporting.AllureAttachments;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.qameta.allure.Allure;
import java.util.Deque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;

/**
 * API scenario lifecycle: tracking links, failure attachments, LIFO cleanup, transport disposal.
 */
public class Hooks {

    private static final Logger log = LoggerFactory.getLogger(Hooks.class);
    private final ScenarioContext ctx;

    public Hooks(ScenarioContext ctx) {
        this.ctx = ctx;
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
        if (!scenario.isFailed()) {
            return;
        }
        try {
            var response = ctx.lastResponse();
            AllureAttachments.text(
                    "Failed call",
                    ctx.lastCall()
                            + " -> "
                            + response.status()
                            + " ("
                            + ctx.lastElapsedMs()
                            + " ms)");
        } catch (IllegalStateException noRequestYet) {
            AllureAttachments.text(
                    "Failed before any request", "Check preconditions / step arguments");
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
        ctx.close();
        log.info("{} {}", scenario.isFailed() ? "FAILED" : "PASSED", scenario.getName());
    }
}
