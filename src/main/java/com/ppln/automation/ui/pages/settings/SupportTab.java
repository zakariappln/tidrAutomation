package com.ppln.automation.ui.pages.settings;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.ppln.automation.ui.pages.components.BaseComponent;

/**
 * Settings &gt; Help &amp; Support ({@code support.html}): an info text and "Get Diagnostics",
 * which opens {@code /en/settings/send-diagnostics} (a tar.gz download) in a new window.
 */
public final class SupportTab extends BaseComponent {

    public SupportTab(Page page) {
        super(page, page.locator("#support"));
    }

    public Locator links() {
        return root.locator("a[href]");
    }

    public Locator getDiagnosticsButton() {
        return root.getByRole(
                AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Get Diagnostics").setExact(true));
    }
}
