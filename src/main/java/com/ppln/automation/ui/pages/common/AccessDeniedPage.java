package com.ppln.automation.ui.pages.common;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * ThreatIDR's page-level denial: {@code templates/401.html} rendered with HTTP 200 in place of the
 * requested page (not a redirect, not a 403). It extends the guest layout, so there is no sidebar;
 * the only stable marker is its heading "401 Unauthorized!".
 */
public final class AccessDeniedPage {

    private final Locator heading;

    public AccessDeniedPage(Page page) {
        heading =
                page.getByRole(
                        AriaRole.HEADING, new Page.GetByRoleOptions().setName("401 Unauthorized!"));
    }

    public Locator heading() {
        return heading;
    }
}
