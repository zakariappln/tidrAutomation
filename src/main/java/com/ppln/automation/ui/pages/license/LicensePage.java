package com.ppln.automation.ui.pages.license;

import com.microsoft.playwright.Page;
import com.ppln.automation.ui.pages.BasePage;

/**
 * License page ({@code /<lang>/license}, templates/home/license.html). There is no key input: with
 * no stored license it shows "No Active License Found!" and an "Activate your license" button that
 * POSTs {@code /<lang>/save-license} with an empty body.
 */
public final class LicensePage extends BasePage {

    /** JSON widget endpoint behind the dashboard's license info: {@code {"data": "..."}}. */
    public static final String LICENSE_INFO_PATH = "/en/dashboard/license-info";

    public LicensePage(Page page) {
        super(page);
    }

    @Override
    protected String path() {
        return "/en/license";
    }
}
