package com.ppln.automation.ui.pages.domains;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.ppln.automation.ui.pages.BasePage;
import com.ppln.automation.ui.pages.components.DomainTableComponent;
import io.qameta.allure.Step;

/**
 * Allowed Domains ({@code /<lang_code>/allowed_domains/}, template {@code whitelisted_domain}).
 * Same markup as {@link BlockedDomainsPage} - see its javadoc for why "Add Domain" is located by
 * text rather than role (an {@code <a>} with no href has no accessible role).
 */
public final class AllowedDomainsPage extends BasePage implements DomainListPage {

    private final Locator addAllowDomainLink;
    private final Locator domainInput;
    private final Locator domainError;
    private final Locator addDomainSubmitLink;
    private final Locator confirmDeleteButton;
    private final DomainTableComponent table;

    public AllowedDomainsPage(Page page) {
        super(page);
        addAllowDomainLink =
                page.getByRole(
                        AriaRole.LINK, new Page.GetByRoleOptions().setName("Add Allow Domain"));
        domainInput = page.getByPlaceholder("Enter Domain");
        // the error div has no role or label; its id is the template's own, stable name
        domainError = page.locator("#error_add_whitelisted_domain_domain");
        addDomainSubmitLink =
                page.getByText("Add Domain", new Page.GetByTextOptions().setExact(true));
        confirmDeleteButton =
                page.getByRole(
                        AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Yes, Delete it"));
        table = new DomainTableComponent(page, "whitelisted-domain-list");
    }

    @Override
    protected String path() {
        return "/en/allowed_domains/";
    }

    @Override
    public DomainTableComponent table() {
        return table;
    }

    @Override
    @Step("Open allowed domains list page {number}")
    public AllowedDomainsPage openListPage(int number) {
        page.navigate(path() + "?page=" + number);
        return this;
    }

    @Override
    @Step("Go to page {number} of the allowed domains list")
    public AllowedDomainsPage goToPage(int number) {
        table.pageLink(number).click();
        return this;
    }

    @Override
    @Step("Add allowed domain {domain}")
    public AllowedDomainsPage addDomain(String domain) {
        openAddForm();
        domainInput.fill(domain);
        addDomainSubmitLink.click();
        return this;
    }

    @Override
    @Step("Type a value into the allowed domain field")
    public AllowedDomainsPage typeDomain(String value) {
        openAddForm();
        domainInput.pressSequentially(value);
        return this;
    }

    @Override
    @Step("Delete allowed domain {domain}")
    public AllowedDomainsPage deleteDomain(String domain) {
        deleteButtonIn(rowFor(domain)).click();
        confirmDeleteButton.click();
        return this;
    }

    /** The add form replaces the list; its toggle link hides itself while the form is open. */
    private void openAddForm() {
        if (!domainInput.isVisible()) {
            addAllowDomainLink.click();
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
