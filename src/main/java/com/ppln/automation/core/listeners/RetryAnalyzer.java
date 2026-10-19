package com.ppln.automation.core.listeners;

import com.ppln.automation.core.config.ConfigManager;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * Bounded retry for plain TestNG tests ({@code retry.count}, default 1). Retried passes surface in
 * Allure under "Flaky (passed on retry)"; retries are never applied to Cucumber scenarios.
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private int attempt = 0;

    @Override
    public boolean retry(ITestResult result) {
        return attempt++ < ConfigManager.config().retryCount();
    }
}
