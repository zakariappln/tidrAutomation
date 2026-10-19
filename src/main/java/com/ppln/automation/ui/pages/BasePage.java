package com.ppln.automation.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;

/**
 * Base for every page object. Pages expose {@link Locator}s and business actions; they never assert
 * and never wait explicitly (Playwright auto-waits; tests use web-first assertions).
 */
public abstract class BasePage {

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }

    /** Route relative to the BrowserContext baseURL, e.g. {@code /login}. */
    protected abstract String path();

    @Step("Open page")
    public <T extends BasePage> T open() {
        page.navigate(path());
        @SuppressWarnings("unchecked")
        T self = (T) this;
        return self;
    }

    public Page page() {
        return page;
    }

    protected Locator byTestId(String id) {
        return page.getByTestId(id);
    }
}
