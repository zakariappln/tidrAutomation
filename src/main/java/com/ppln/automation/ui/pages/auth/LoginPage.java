package com.ppln.automation.ui.pages.auth;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.ppln.automation.ui.pages.BasePage;
import com.ppln.automation.ui.pages.dashboard.DashboardPage;
import io.qameta.allure.Step;

/**
 * Login screen ({@code /<lang_code>/}, e.g. {@code /en/}). Locators confirmed live against
 * ThreatIDR v1.5.1 (tidr-deploy-v1) on 2026-10-17: the email/password inputs have no real {@code
 * <label>}, only a {@code placeholder}, so {@code getByLabel} does not match them.
 */
public final class LoginPage extends BasePage {

    private final Locator emailInput;
    private final Locator passwordInput;
    private final Locator signInButton;
    private final Locator errorBanner;

    public LoginPage(Page page) {
        super(page);
        // ids, not the placeholders / button text: the same form is served translated under
        // /ja/ ("メールアドレス", "サインイン"), and the localization feature logs in from there
        emailInput = page.locator("#loginForm #email");
        passwordInput = page.locator("#loginForm #password");
        signInButton = page.locator("#loginForm button[type=submit]");
        // the login page hides its flash (#flashes, role=alert) 3 s after DOMContentLoaded, so a
        // text mismatch would otherwise be reported as "element(s) not found" once it is hidden;
        // including hidden elements keeps the server's message in the assertion's evidence
        errorBanner =
                page.getByRole(AriaRole.ALERT, new Page.GetByRoleOptions().setIncludeHidden(true));
    }

    @Override
    protected String path() {
        return "/en/";
    }

    @Step("Log in as {username}")
    public DashboardPage loginAs(String username, String password) {
        fillCredentials(username, password);
        signInButton.click();
        return new DashboardPage(page);
    }

    @Step("Attempt login as {username} expecting an error")
    public LoginPage loginExpectingError(String username, String password) {
        fillCredentials(username, password);
        signInButton.click();
        return this;
    }

    public Locator errorBanner() {
        return errorBanner;
    }

    public Locator signInButton() {
        return signInButton;
    }

    /**
     * The input whose placeholder (the only label it has) is {@code label}: "Email", "Password".
     */
    public Locator field(String label) {
        return page.getByPlaceholder(label, new Page.GetByPlaceholderOptions().setExact(true));
    }

    /** Present only on the login screen; used to confirm an anonymous visitor landed here. */
    public Locator emailInput() {
        return emailInput;
    }

    private void fillCredentials(String username, String password) {
        emailInput.fill(username);
        passwordInput.fill(password);
    }
}
