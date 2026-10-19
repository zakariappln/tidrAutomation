package com.ppln.automation.ui.base;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.ppln.automation.core.config.ConfigManager;
import com.ppln.automation.core.reporting.AllureAttachments;
import com.ppln.automation.ui.driver.PlaywrightFactory;
import java.nio.file.Path;
import org.testng.ITestResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

/**
 * Base for plain TestNG UI tests: a fresh {@link Page} per test method, tracing/video/screenshot
 * attached to Allure on failure. Listeners are registered in the suite XML, not here, so the same
 * class works from any suite.
 */
public abstract class BaseUiTest {

    protected Page page;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        PlaywrightAssertions.setDefaultAssertionTimeout(ConfigManager.config().expectTimeoutMs());
        page = PlaywrightFactory.startContext(storageState());
        page.onConsoleMessage(AllureAttachments::collectConsole);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        boolean failed = result.getStatus() == ITestResult.FAILURE;
        String name = result.getMethod().getMethodName();
        Path video = null;
        if (page != null && !page.isClosed()) {
            if (failed) {
                AllureAttachments.screenshot(page, "Failure screenshot");
                AllureAttachments.text("Page URL", page.url());
            }
            AllureAttachments.consoleLogs();
            video =
                    failed && page.video() != null
                            ? page.video().path()
                            : null; // path must be read before close
        }
        Path trace = PlaywrightFactory.stopContext(name, failed);
        if (trace != null) {
            AllureAttachments.trace(trace);
        }
        if (video != null) {
            AllureAttachments.video(video);
        }
        page = null;
    }

    @AfterClass(alwaysRun = true)
    public void closeBrowser() {
        PlaywrightFactory.shutdown();
    }

    /** Override to run authenticated: {@code return StorageStateManager.forRole("admin");}. */
    protected Path storageState() {
        return null;
    }
}
