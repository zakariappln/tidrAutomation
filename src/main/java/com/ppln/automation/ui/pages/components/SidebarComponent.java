package com.ppln.automation.ui.pages.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * Left navigation ({@code templates/sidebar.html}, root {@code .sidenav}). Entries are links whose
 * accessible name is the label ("Reports", "Users", ...). Some entries ("Profile", "Settings",
 * "Logout") are also rendered unconditionally as {@code visible-xs} copies for small screens, so
 * only <em>visible</em> links count as "shown".
 */
public final class SidebarComponent extends BaseComponent {

    public SidebarComponent(Page page) {
        super(page, page.locator(".sidenav"));
    }

    /** Visible menu links with exactly this label. */
    public Locator menuLink(String label) {
        return root.getByRole(
                        AriaRole.LINK, new Locator.GetByRoleOptions().setName(label).setExact(true))
                .filter(new Locator.FilterOptions().setVisible(true));
    }
}
