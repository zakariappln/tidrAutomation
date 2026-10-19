package com.ppln.automation.core.listeners;

/**
 * Detects Cucumber-TestNG runner classes by name so {@code core} needs no compile dependency on
 * Cucumber (which is test-scoped).
 */
final class CucumberRunners {

    private static final String RUNNER_BASE = "io.cucumber.testng.AbstractTestNGCucumberTests";

    private CucumberRunners() {}

    static boolean isRunner(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (RUNNER_BASE.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }
}
