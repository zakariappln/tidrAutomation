# Hooks and Cleanup

One `Hooks` class. PicoContainer injects the same `ScenarioContext` the step classes receive, so hooks see exactly the scenario's state.

```java
package com.company.automation.api.bdd.steps;

import com.company.automation.api.bdd.context.ScenarioContext;
import com.company.automation.core.reporting.AllureAttachments;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.qameta.allure.Allure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Deque;

public class Hooks {

    private static final Logger log = LoggerFactory.getLogger(Hooks.class);
    private final ScenarioContext ctx;

    public Hooks(ScenarioContext ctx) { this.ctx = ctx; }

    @Before(order = 0)
    public void start(Scenario scenario) {
        log.info("▶ {} [{}]", scenario.getName(), String.join(" ", scenario.getSourceTagNames()));
        scenario.getSourceTagNames().stream()
            .filter(t -> t.startsWith("@TC-") || t.startsWith("@JIRA-"))
            .forEach(t -> Allure.tms(t.substring(1), null));
    }

    /** Skip scenarios tagged @wip unless explicitly requested. */
    @Before(value = "@wip and not @run-wip", order = 1)
    public void skipWip(Scenario scenario) {
        throw new org.testng.SkipException("Scenario tagged @wip");
    }

    @After(order = 100)                     // runs FIRST among @After hooks (higher order = earlier for After)
    public void attachOnFailure(Scenario scenario) {
        if (!scenario.isFailed()) return;
        try {
            var r = ctx.lastResponse();
            AllureAttachments.text("Failed call", ctx.lastCall() + " → " + r.status());
            AllureAttachments.text("Stored variables", ctx.variablesForReport());
        } catch (IllegalStateException noRequestYet) {
            AllureAttachments.text("Failed before any request", "Check preconditions / step arguments");
        }
    }

    @After(order = 50)
    public void runCleanups(Scenario scenario) {
        Deque<Runnable> cleanups = ctx.cleanups();
        int failed = 0;
        while (!cleanups.isEmpty()) {
            try { cleanups.pop().run(); }
            catch (RuntimeException e) { failed++; log.warn("cleanup failed in '{}': {}", scenario.getName(), e.toString()); }
        }
        if (failed > 0) AllureAttachments.text("Cleanup warnings", failed + " cleanup action(s) failed; see log");
    }

    @After(order = 0)                        // runs LAST
    public void dispose(Scenario scenario) {
        ctx.close();
        log.info("{} {}", scenario.isFailed() ? "✖" : "✔", scenario.getName());
    }
}
```

## Cleanup contract

| Who                                   | Does                                                             |
|---------------------------------------|------------------------------------------------------------------|
| Any step that **creates** data        | `ctx.onCleanup(() -> client.delete(id))` immediately after success |
| `And the created X {id} is deleted`   | deletes now, tolerates 404; the registered cleanup then becomes a harmless 404 |
| `@cleanup` tag                        | documentation for readers; behaviour is identical because registration is automatic |
| `runCleanups` hook                    | pops LIFO (children before parents), never throws, reports failures as an Allure attachment |
| `dispose` hook                        | disposes `APIRequestContext`s and `Playwright`, always            |

Ordering guarantee: attachments → cleanups → dispose. Cleanup must not run before the failure attachments, or the "stored variables" attachment would be missing the ids the reviewer needs.

## ScenarioContext additions used here

```java
public String variablesForReport() {
    return variables.entrySet().stream()
        .filter(e -> !e.getKey().startsWith("__"))
        .map(e -> e.getKey() + " = " + e.getValue())
        .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
}
```

## Things hooks must not do

- No retries of the scenario (Cucumber-TestNG rerun is handled by TestNG's `RetryAnalyzer`, and only if the project consciously enables it).
- No `Thread.sleep` "settle" delays after cleanup.
- No login in `@Before` — tokens are resolved lazily by `TokenProvider` and cached per role for the JVM, so scenarios that never authenticate pay nothing.
- No static counters or shared collections; anything cross-scenario belongs in `TokenProvider`/`SchemaRegistry`, which are immutable-after-load by design.
