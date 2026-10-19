package com.ppln.automation.ui.pages.domains;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.ppln.automation.ui.pages.BasePage;
import com.ppln.automation.ui.pages.components.DomainTableComponent;
import io.qameta.allure.Step;

/**
 * Blocked Domains ({@code /<lang_code>/blocked_domains/}). Confirmed live against ThreatIDR v1.5.1
 * on 2026-10-17: "Add Block Domain" and "Cancel" are {@code <a href="javascript:void(0)">} tags,
 * which DO get an implicit accessible role=link (a present, if no-op, href is enough). "Add Domain"
 * is an {@code <a type="submit" onclick="addBlockedDomain(event)">} with NO href attribute at all,
 * which per the ARIA spec gets NO implicit role (confirmed: role is null, not "link") -
 * getByRole(LINK, ...) correctly never matches it, despite it being visible on screen; {@link
 * Page#getByText} is the only applicable locator tier here. "Delete" and "Apply Changes" are real
 * {@code <button>}s ("Apply Changes" only while the page lists at least one row). The delete
 * confirmation dialog's body text contains the app's own typo, "permenantly" - matched loosely here
 * so a future spelling fix doesn't break this locator.
 */
public final class BlockedDomainsPage extends BasePage implements DomainListPage {

    private final Locator addBlockDomainLink;
    private final Locator domainInput;
    private final Locator domainError;
    private final Locator addDomainSubmitLink;
    private final Locator confirmDeleteButton;
    private final DomainTableComponent table;

    public BlockedDomainsPage(Page page) {
        super(page);
        addBlockDomainLink =
                page.getByRole(
                        AriaRole.LINK, new Page.GetByRoleOptions().setName("Add Block Domain"));
        domainInput = page.getByPlaceholder("Enter Domain");
        // the error div has no role or label; its id is the template's own, stable name
        domainError = page.locator("#error_add_blocked_domain_domain");
        addDomainSubmitLink =
                page.getByText("Add Domain", new Page.GetByTextOptions().setExact(true));
        confirmDeleteButton =
                page.getByRole(
                        AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Yes, Delete it"));
        table = new DomainTableComponent(page, "blocked-domain-list");
    }

    @Override
    protected String path() {
        return "/en/blocked_domains/";
    }

    @Override
    public DomainTableComponent table() {
        return table;
    }

    @Override
    @Step("Open blocked domains list page {number}")
    public BlockedDomainsPage openListPage(int number) {
        page.navigate(path() + "?page=" + number);
        return this;
    }

    @Override
    @Step("Go to page {number} of the blocked domains list")
    public BlockedDomainsPage goToPage(int number) {
        table.pageLink(number).click();
        return this;
    }

    @Override
    @Step("Add blocked domain {domain}")
    public BlockedDomainsPage addDomain(String domain) {
        openAddForm();
        domainInput.fill(domain);
        addDomainSubmitLink.click();
        return this;
    }

    @Override
    @Step("Type a value into the blocked domain field")
    public BlockedDomainsPage typeDomain(String value) {
        openAddForm();
        domainInput.pressSequentially(value);
        return this;
    }

    @Override
    @Step("Delete blocked domain {domain}")
    public BlockedDomainsPage deleteDomain(String domain) {
        deleteButtonIn(rowFor(domain)).click();
        confirmDeleteButton.click();
        return this;
    }

    /** The add form replaces the list; its toggle link hides itself while the form is open. */
    private void openAddForm() {
        if (!domainInput.isVisible()) {
            addBlockDomainLink.click();
        }
    }

    @Override
    public Locator domainInput() {
        return domainInput;
    }

    @Override
    public Locator domainError() {
        return domainError;
    }

    /** The row's Delete button; rendered only for users holding "user-delete". */
    public Locator deleteButtonIn(Locator row) {
        return row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Delete"));
    }
}
