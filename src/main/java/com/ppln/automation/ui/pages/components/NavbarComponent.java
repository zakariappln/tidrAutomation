package com.ppln.automation.ui.pages.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.ppln.automation.ui.pages.auth.LoginPage;
import io.qameta.allure.Step;

/**
 * The top header present on every authenticated page: profile dropdown (Profile / Settings /
 * Logout) and the language switcher. Confirmed live against ThreatIDR v1.5.1 on 2026-10-17: the
 * profile toggle is an unlabelled {@code a.dropdown-toggle} (no aria-label, text varies by user's
 * display name), identified instead by the dropdown menu it opens containing "Logout"; clicking
 * "Logout" opens a confirmation dialog ("Are you sure? This will log you out from the system").
 */
public final class NavbarComponent extends BaseComponent {

    private final Locator profileToggle;
    private final Locator logoutLink;
    private final Locator confirmLogoutButton;

    public NavbarComponent(Page page) {
        super(page, page.locator("body"));
        profileToggle =
                page.locator(".dropdown", new Page.LocatorOptions().setHasText("Logout"))
                        .locator(".dropdown-toggle");
        logoutLink = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Logout"));
        confirmLogoutButton =
                page.getByRole(
                        AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Yes, Log out"));
    }

    /**
     * The bell ({@code div#notification-bell-icon}, nav.html; no role or label). Clicking it posts
     * the ids of the last notification push to {@code /dashboard/notification-update}.
     */
    public Locator notificationBell() {
        return page.locator("#notification-bell-icon");
    }

    /** The red dot shown on the bell while the last push reported unread alerts. */
    public Locator unreadAlertsDot() {
        return page.locator("#notification-status-icon");
    }

    @Step("Open the alert notification feed")
    public NavbarComponent openNotifications() {
        notificationBell().click();
        return this;
    }

    @Step("Log out")
    public LoginPage logout() {
        profileToggle.click();
        logoutLink.click();
        confirmLogoutButton.click();
        return new LoginPage(page);
    }
}
