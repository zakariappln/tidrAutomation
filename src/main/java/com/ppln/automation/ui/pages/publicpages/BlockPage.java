package com.ppln.automation.ui.pages.publicpages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.ppln.automation.ui.pages.BasePage;

/**
 * The public block page bind sends blocked users to ({@code /<lang_code>/nxdomain?host=<h>}, {@code
 * templates/home/nxdomain.html}): heading "ThreatIDR Protect You From &lt;threat type&gt;" ("...
 * From Malware" when no threat matches) and a "Domain Name: &lt;host&gt;" button.
 */
public final class BlockPage extends BasePage {

    public static final String HEADING_PREFIX = "ThreatIDR Protect You From ";

    private final Locator heading;

    public BlockPage(Page page) {
        super(page);
        heading = page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1));
    }

    @Override
    protected String path() {
        return "/en/nxdomain";
    }

    public Locator heading() {
        return heading;
    }

    /** The "Domain Name: ..." button echoing the host parameter. */
    public Locator domainNameButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Domain Name:"));
    }
}
