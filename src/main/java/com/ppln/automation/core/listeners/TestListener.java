package com.ppln.automation.core.listeners;

import com.ppln.automation.core.reporting.AllureAttachments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * Lifecycle logging for plain TestNG tests. Browser artifacts are attached by {@code BaseUiTest}
 * (the context must be closed before trace/video exist); API request/response pairs are attached by
 * {@code ApiClient} at call time. Cucumber scenarios are handled by their own hooks.
 */
public class TestListener implements ITestListener {

    private static final Logger log = LoggerFactory.getLogger(TestListener.class);

    @Override
    public void onTestStart(ITestResult result) {
        if (!CucumberRunners.isRunner(result.getTestClass().getRealClass())) {
            log.info("START {}", name(result));
        }
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        if (!CucumberRunners.isRunner(result.getTestClass().getRealClass())) {
            log.info(
                    "PASS  {} ({} ms)",
                    name(result),
                    result.getEndMillis() - result.getStartMillis());
        }
    }

    @Override
    public void onTestFailure(ITestResult result) {
        if (CucumberRunners.isRunner(result.getTestClass().getRealClass())) {
            return;
        }
        log.error("FAIL  {}", name(result), result.getThrowable());
        if (result.getThrowable() != null) {
            AllureAttachments.text("Failure", String.valueOf(result.getThrowable()));
        }
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        if (!CucumberRunners.isRunner(result.getTestClass().getRealClass())) {
            log.warn("SKIP  {}", name(result));
        }
    }

    private static String name(ITestResult result) {
        return result.getTestClass().getRealClass().getSimpleName()
                + "."
                + result.getMethod().getMethodName();
    }
}
