package com.ppln.automation.ui.pages.modules;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.ppln.automation.ui.pages.BasePage;

/**
 * DGA Domains ({@code /<lang>/dga_domains/}, no menu entry). Written against the shipped {@code
 * _dga_domain/index.html} (the view renders the missing {@code dga_domain/index.html}, so the live
 * page is an HTTP 500): heading "Detect DGA Domain" and a table with Domains, Detection at, Log
 * source time, Source IP, Total hits, Threat Source.
 */
public final class DgaDomainsPage extends BasePage {

    public DgaDomainsPage(Page page) {
        super(page);
    }

    @Override
    protected String path() {
        return "/en/dga_domains/";
    }

    public Locator heading() {
        return page.getByRole(
                AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("Detect DGA Domain").setExact(true));
    }

    public Locator domainsColumnHeader() {
        return page.getByRole(
                AriaRole.COLUMNHEADER,
                new Page.GetByRoleOptions().setName("Domains").setExact(true));
    }
}
