package com.ppln.automation.ui.bdd.context;

import com.ppln.automation.core.reporting.AllureAttachments;
import io.qameta.allure.Allure;
import io.qameta.allure.model.Status;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Soft verification for long end-to-end scenarios. With the {@code @soft} tag a failed check is
 * recorded (message, Allure sub-step, screenshot) and the scenario goes on; the {@code @After} hook
 * then fails the scenario with every recorded failure. Without the tag checks fail immediately.
 * Only verification steps use it; actions (login, navigation, submit) always fail hard.
 */
public final class SoftChecks {

    private static final Logger log = LoggerFactory.getLogger(SoftChecks.class);

    private final UiScenarioContext ctx;
    private final List<String> failures = new ArrayList<>();
    private boolean enabled;

    public SoftChecks(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    public void enable(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public void check(String step, Runnable assertion) {
        if (!enabled) {
            assertion.run();
            return;
        }
        try {
            assertion.run();
        } catch (AssertionError | RuntimeException e) {
            // RuntimeException covers Playwright timeouts and lookups that found no row/column.
            String reason = firstLines(e.getMessage(), 3);
            failures.add(step + System.lineSeparator() + "    " + reason);
            log.warn("SOFT FAILURE in '{}': {}", step, reason);
            Allure.step("SOFT FAILURE: " + step, Status.FAILED);
            AllureAttachments.text("Soft failure: " + step, e.toString());
            if (ctx.hasPage()) {
                AllureAttachments.screenshot(ctx.page(), "Soft failure: " + step);
            }
        }
    }

    /**
     * Renders a step pattern with its actual arguments: {@code {string}} / {@code {int}} / {@code
     * {word}} placeholders are filled in order; regex patterns are returned as written.
     */
    public static String describe(String pattern, Object... args) {
        int[] next = {0};
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\\{(string|int|word|float)\\}").matcher(pattern);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object arg = next[0] < args.length ? args[next[0]++] : "?";
            String text = m.group(1).equals("string") ? "\"" + arg + "\"" : String.valueOf(arg);
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(text));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public List<String> failures() {
        return List.copyOf(failures);
    }

    private static String firstLines(String message, int lines) {
        if (message == null) {
            return "";
        }
        String[] parts = message.strip().split("\\R");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(lines, parts.length); i++) {
            out.append(i > 0 ? " | " : "").append(parts[i].strip());
        }
        return out.toString();
    }
}
